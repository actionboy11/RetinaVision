package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.pojo.DTO.CaseInsertDTO;
import com.example.retinavision.pojo.DTO.CaseListQueryDTO;
import com.example.retinavision.pojo.DTO.CaseUpdateDTO;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.CaseService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CaseServiceImpl implements CaseService {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final CaseMapper caseMapper;

    public CaseServiceImpl(CaseMapper caseMapper) {
        this.caseMapper = caseMapper;
    }

    @Override
    public PageResult<CaseListItemVO> getCaseList(CaseListQueryDTO queryDTO) {
        // 1. Controller 只负责接收请求；分页默认值和边界保护统一放在 Service 层处理。
        CaseListQueryDTO safeQuery = queryDTO == null ? new CaseListQueryDTO() : queryDTO;

        int pageNo = normalizePageNo(safeQuery.getPageNo());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        int offset = (pageNo - 1) * pageSize;

        // 2. keyword 先 trim；如果 trim 后为空，传给 Mapper 的就是 null，SQL 就不会拼接 keyword 条件。
        safeQuery.setKeyword(normalizeKeyword(safeQuery.getKeyword()));

        // 3. 分页查询通常需要两条 SQL：total 用于分页器，records 用于当前页表格。
        long total = caseMapper.countCasePage(safeQuery);
        List<CaseListItemVO> records = caseMapper.selectCasePage(safeQuery, offset, pageSize);

        // 4. PageResult 的字段要和前端 src/types/common.ts 保持一致：records/total/pageNo/pageSize。
        return new PageResult<>(records, total, pageNo, pageSize);
    }


    @Override
    public CaseListItemVO addCase(CaseInsertDTO caseInsertDTO, Integer userid) {
        // 补充：请求体为空时不能继续读取字段，否则会触发 NullPointerException 并变成 500。
        if (caseInsertDTO == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.PARAM_ERROR_MSG);
        }

        // 补充：patientCode 先统一 trim，避免 " P001 " 和 "P001" 被当成两个不同患者编号。
        String patientCode = normalizePatientCode(caseInsertDTO.getPatientCode());
        if (!StringUtils.hasText(patientCode)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.CASE_CODE_REQUIRED);
        }
        if (patientCode.length() < 3 || patientCode.length() > 64) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.CASE_CODELENGTH_ERROR);
        }

        // 补充：创建病例时性别和眼别是业务必需字段，提前拦截可以让前端收到明确的 40000。
        if (caseInsertDTO.getPatientGender() == null || caseInsertDTO.getEyeSide() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.CASE_REQUIRED_FIELD_ERROR);
        }

        validatePatientAge(caseInsertDTO.getPatientAge());

        // 查询患者编号是否重复
        LambdaQueryWrapper<CaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CaseEntity::getPatientCode, patientCode);
        // exists 也是 BaseMapper 自带的方法
        if (caseMapper.exists(wrapper)) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, ErrorMessageContant.CASE_ALREADY_EXISTS);
        }

        CaseEntity caseEntity = new CaseEntity();
        String caseNo = generateCaseNo();
        caseEntity.setPatientCode(patientCode);
        caseEntity.setPatientAge(caseInsertDTO.getPatientAge());
        caseEntity.setPatientGender(caseInsertDTO.getPatientGender());
        caseEntity.setEyeSide(caseInsertDTO.getEyeSide());
        caseEntity.setDiagnosisNote(caseInsertDTO.getDiagnosisNote());
        caseEntity.setStatus(CaseStatus.ACTIVE);
        caseEntity.setCreatedBy(userid);
        caseEntity.setCreatedAt(LocalDateTime.now());
        caseEntity.setUpdatedAt(LocalDateTime.now());
        caseEntity.setCaseNo(caseNo);
        //保存新病例到数据库
        caseMapper.insert(caseEntity);
        //返回病例信息VO
        CaseListItemVO caseListItemVO = caseMapper.getCaseById(caseEntity.getId());
        return caseListItemVO;
    }


    @Override
    public CaseListItemVO getCaseById(Integer caseId) {
        CaseEntity caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.CASE_NOT_EXISTS);
        }
        if (caseEntity.getStatus() == CaseStatus.DELETED){
            throw new  BaseException(ErrorMessageSignal.NOT_FOUND,ErrorMessageContant.CASE_DELETED_MSG);
        }
        CaseListItemVO caseListItemVO = caseMapper.getCaseById(caseEntity.getId());
        return caseListItemVO;

    }

    @Override
    public CaseListItemVO updateCase(Integer caseId, CaseUpdateDTO caseUpdateDTO) {
        // 补充：更新接口以路径中的 caseId 为准；请求体为空直接返回参数错误。
        if (caseUpdateDTO == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.PARAM_ERROR_MSG);
        }
        CaseEntity caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.CASE_NOT_EXISTS);
        }
        if (caseEntity.getStatus() == CaseStatus.DELETED){
            throw new  BaseException(ErrorMessageSignal.NOT_FOUND,ErrorMessageContant.CASE_DELETED_MSG);
        }

        if (isEmptyUpdate(caseUpdateDTO)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.CASE_UPDATE_EMPTY);
        }

        // 补充：不要重新 builder 一个没有 id 的实体；直接修改数据库查出的原实体，才能保证 updateById 命中当前病例。
        if (caseUpdateDTO.getPatientAge() != null) {
            validatePatientAge(caseUpdateDTO.getPatientAge());
            caseEntity.setPatientAge(caseUpdateDTO.getPatientAge());
        }
        if (caseUpdateDTO.getPatientGender() != null) {
            caseEntity.setPatientGender(caseUpdateDTO.getPatientGender());
        }
        if (caseUpdateDTO.getEyeSide() != null) {
            caseEntity.setEyeSide(caseUpdateDTO.getEyeSide());
        }
        if (caseUpdateDTO.getDiagnosisNote() != null) {
            caseEntity.setDiagnosisNote(caseUpdateDTO.getDiagnosisNote());
        }
        if (caseUpdateDTO.getStatus() != null) {
            // 补充：删除/归档应由专门接口承载；更新接口只允许普通状态变更，避免误把病例软删除。
            if (caseUpdateDTO.getStatus() == CaseStatus.DELETED) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.CASE_STATUS_DELETE_FORBIDDEN);
            }
            caseEntity.setStatus(caseUpdateDTO.getStatus());
        }

        caseEntity.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(caseEntity);
        CaseListItemVO caseListItemVO = caseMapper.getCaseById(caseId);
        return caseListItemVO;
    }

    @Override
    public void deleteCase(Integer caseId) {

        CaseEntity caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.CASE_NOT_EXISTS);
        }
        if (caseEntity.getStatus() == CaseStatus.DELETED) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.CASE_DELETED_MSG);
        }
        // TODO 任务表完成后：删除前查询该病例是否存在 CREATED/WAITING/RUNNING/RETRYING 等未结束任务；存在则返回 40900。
        caseEntity.setStatus(CaseStatus.DELETED);
        caseEntity.setDeletedAt(LocalDateTime.now());
        caseEntity.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(caseEntity);

    }

    private int normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return DEFAULT_PAGE_NO;
        }

        return pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String normalizePatientCode(String patientCode) {
        return StringUtils.hasText(patientCode) ? patientCode.trim() : null;
    }

    private void validatePatientAge(Integer patientAge) {
        // 补充：patientAge 是 Integer，允许不传；只有传入时才做范围校验，避免空指针。
        if (patientAge != null && (patientAge < 0 || patientAge > 120)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.CASE_AGE_ERROR);
        }
    }

    private boolean isEmptyUpdate(CaseUpdateDTO caseUpdateDTO) {
        return caseUpdateDTO.getPatientAge() == null
                && caseUpdateDTO.getPatientGender() == null
                && caseUpdateDTO.getEyeSide() == null
                && caseUpdateDTO.getDiagnosisNote() == null
                && caseUpdateDTO.getStatus() == null;
    }

    private String generateCaseNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(100, 1000);
        return "C" + timestamp + random;
    }
}
