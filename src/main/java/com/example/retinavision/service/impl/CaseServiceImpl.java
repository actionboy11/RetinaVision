package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.DTO.CaseDoctorAssignmentDTO;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.Entity.PatientProfileEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.pojo.DTO.CaseInsertDTO;
import com.example.retinavision.pojo.DTO.CaseListQueryDTO;
import com.example.retinavision.pojo.DTO.CaseUpdateDTO;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.CaseService;
import com.example.retinavision.service.PatientProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final TaskMapper taskMapper;
    private final UserRegisterMapper userMapper;
    private final ImageMapper imageMapper;
    private final PatientProfileService patientProfiles;

    public CaseServiceImpl(CaseMapper caseMapper, TaskMapper taskMapper, UserRegisterMapper userMapper,
                           ImageMapper imageMapper, PatientProfileService patientProfiles) {
        this.caseMapper = caseMapper;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.imageMapper = imageMapper;
        this.patientProfiles = patientProfiles;
    }

    @Override
    public PageResult<CaseListItemVO> getCaseList(CaseListQueryDTO queryDTO, CurrentUserVO user) {
        // 1. Controller 只负责接收请求；分页默认值和边界保护统一放在 Service 层处理。
        CaseListQueryDTO safeQuery = queryDTO == null ? new CaseListQueryDTO() : queryDTO;

        int pageNo = normalizePageNo(safeQuery.getPageNo());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        int offset = (pageNo - 1) * pageSize;

        // 2. keyword 先 trim；如果 trim 后为空，传给 Mapper 的就是 null，SQL 就不会拼接 keyword 条件。
        safeQuery.setKeyword(normalizeKeyword(safeQuery.getKeyword()));

        // 3. 分页查询通常需要两条 SQL：total 用于分页器，records 用于当前页表格。
        requireCaseRole(user);
        Integer patientAccountUserId = user.getRoleCode() == UserRole.USER ? user.getId() : null;
        Integer doctorId = user.getRoleCode() == UserRole.DOCTOR ? user.getId() : null;
        long total = caseMapper.countCasePage(safeQuery, patientAccountUserId, doctorId);
        List<CaseListItemVO> records = caseMapper.selectCasePage(
                safeQuery, patientAccountUserId, doctorId, offset, pageSize);
        if (user.getRoleCode() == UserRole.USER) {
            records.forEach(record -> record.setDiagnosisNote(null));
        }

        // 4. PageResult 的字段要和前端 src/types/common.ts 保持一致：records/total/pageNo/pageSize。
        return new PageResult<>(records, total, pageNo, pageSize);
    }


    @Override
    public CaseListItemVO addCase(CaseInsertDTO caseInsertDTO, CurrentUserVO user) {
        // 补充：请求体为空时不能继续读取字段，否则会触发 NullPointerException 并变成 500。
        if (caseInsertDTO == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.PARAM_ERROR_MSG);
        }

        // 补充：创建病例时性别和眼别是业务必需字段，提前拦截可以让前端收到明确的 40000。
        if (caseInsertDTO.getPatientGender() == null || caseInsertDTO.getEyeSide() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.CASE_REQUIRED_FIELD_ERROR);
        }

        validatePatientAge(caseInsertDTO.getPatientAge());
        requireCaseRole(user);
        PatientProfileEntity profile;
        Integer doctorId;
        if (user.getRoleCode() == UserRole.USER) {
            profile = patientProfiles.getOrCreateAccountProfile(user.getId());
            doctorId = requireActiveDoctor(caseInsertDTO.getAssignedDoctorId()).getId();
        } else {
            if (caseInsertDTO.getPatientId() == null) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请选择匿名患者");
            }
            profile = patientProfiles.requireDoctorAccessible(caseInsertDTO.getPatientId(), user.getId());
            doctorId = user.getId();
        }

        CaseEntity caseEntity = new CaseEntity();
        String caseNo = generateCaseNo();
        caseEntity.setPatientId(profile.getId());
        caseEntity.setPatientCode(profile.getPatientNo());
        caseEntity.setPatientAge(caseInsertDTO.getPatientAge());
        caseEntity.setPatientGender(caseInsertDTO.getPatientGender());
        caseEntity.setEyeSide(caseInsertDTO.getEyeSide());
        caseEntity.setDiagnosisNote(caseInsertDTO.getDiagnosisNote());
        caseEntity.setStatus(CaseStatus.ACTIVE);
        caseEntity.setWorkflowStatus(CaseWorkflowStatus.DRAFT);
        caseEntity.setCreatedBy(user.getId());
        caseEntity.setAssignedDoctorId(doctorId);
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
    @Transactional
    public CaseListItemVO assignDoctor(Integer caseId, CaseDoctorAssignmentDTO request, CurrentUserVO user) {
        if (request == null || request.assignedDoctorId() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请选择负责医生");
        }
        CaseEntity medicalCase = caseMapper.selectById(caseId);
        if (medicalCase == null || medicalCase.getStatus() == CaseStatus.DELETED) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.CASE_NOT_EXISTS);
        }
        requirePatientOwns(medicalCase, user);
        if (medicalCase.getWorkflowStatus() != CaseWorkflowStatus.DRAFT) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "病例提交后不能更换负责医生");
        }
        UserEntity doctor = requireActiveDoctor(request.assignedDoctorId());
        if (medicalCase.getAssignedDoctorId() != null
                && !medicalCase.getAssignedDoctorId().equals(doctor.getId())) {
            Long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<TaskEntity>()
                    .eq(TaskEntity::getCaseId, caseId.longValue()));
            if (taskCount != null && taskCount > 0) {
                throw new BaseException(ErrorMessageSignal.CONFLICT, "病例已产生分析任务，不能更换负责医生");
            }
        }
        medicalCase.setAssignedDoctorId(doctor.getId());
        medicalCase.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(medicalCase);
        return caseMapper.getCaseById(caseId);
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
    public CaseListItemVO updateCase(Integer caseId, CaseUpdateDTO caseUpdateDTO, CurrentUserVO user) {
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
        requireCaseAccessForMutation(caseEntity, user);
        if (user.getRoleCode() == UserRole.USER && caseEntity.getWorkflowStatus() != CaseWorkflowStatus.DRAFT) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "病例提交后不能修改关键资料");
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
    public void deleteCase(Integer caseId, CurrentUserVO user) {

        CaseEntity caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.CASE_NOT_EXISTS);
        }
        if (caseEntity.getStatus() == CaseStatus.DELETED) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.CASE_DELETED_MSG);
        }
        requireCaseAccessForMutation(caseEntity, user);
        if (user.getRoleCode() == UserRole.USER && caseEntity.getWorkflowStatus() != CaseWorkflowStatus.DRAFT) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "病例提交后不能删除");
        }
        // TODO 任务表完成后：删除前查询该病例是否存在 CREATED/WAITING/RUNNING/RETRYING 等未结束任务；存在则返回 40900。
        long activeTasks = taskMapper.selectCount(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getCaseId, caseId.longValue())
                .in(TaskEntity::getStatus, TaskStatus.CREATED, TaskStatus.WAITING, TaskStatus.RUNNING, TaskStatus.RETRYING));
        if (activeTasks > 0) throw new com.example.retinavision.exception.ConflictException("病例存在运行中任务，不能删除");
        long successfulTasks = taskMapper.selectCount(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getCaseId, caseId.longValue()).eq(TaskEntity::getStatus, TaskStatus.SUCCESS));
        if (successfulTasks > 0) throw new com.example.retinavision.exception.ConflictException("病例已有分析结果，请改为归档");
        caseEntity.setStatus(CaseStatus.DELETED);
        caseEntity.setDeletedAt(LocalDateTime.now());
        caseEntity.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(caseEntity);

    }

    @Override
    @Transactional
    public CaseListItemVO submit(Integer caseId, CurrentUserVO user) {
        CaseEntity medicalCase = requireActiveCase(caseId);
        requirePatientOwns(medicalCase, user);
        if (medicalCase.getWorkflowStatus() != CaseWorkflowStatus.DRAFT) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "只有草稿病例可以提交");
        }
        if (medicalCase.getAssignedDoctorId() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请先选择负责医生");
        }
        Long imageCount = imageMapper.selectCount(new LambdaQueryWrapper<ImageFileEntity>()
                .eq(ImageFileEntity::getCaseId, caseId.longValue())
                .isNull(ImageFileEntity::getDeletedAt));
        if (imageCount == null || imageCount == 0) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请至少上传一张有效图像后再提交");
        }
        medicalCase.setWorkflowStatus(CaseWorkflowStatus.SUBMITTED);
        medicalCase.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(medicalCase);
        return caseMapper.getCaseById(caseId);
    }

    @Override
    @Transactional
    public CaseListItemVO withdraw(Integer caseId, CurrentUserVO user) {
        CaseEntity medicalCase = requireActiveCase(caseId);
        requirePatientOwns(medicalCase, user);
        if (medicalCase.getWorkflowStatus() != CaseWorkflowStatus.SUBMITTED) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "当前病例不能撤回");
        }
        Long analysisCount = taskMapper.selectCount(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getCaseId, caseId.longValue())
                .eq(TaskEntity::getTaskType, com.example.retinavision.enumeration.TaskType.VESSEL_SEGMENTATION));
        if (analysisCount != null && analysisCount > 0) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "医生已开始分析，病例不能撤回");
        }
        medicalCase.setWorkflowStatus(CaseWorkflowStatus.WITHDRAWN);
        medicalCase.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(medicalCase);
        return caseMapper.getCaseById(caseId);
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

    private UserEntity requireActiveDoctor(Integer doctorId) {
        if (doctorId == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请选择负责医生");
        }
        UserEntity doctor = userMapper.selectById(doctorId);
        if (doctor == null || doctor.getRoleCode() != UserRole.DOCTOR
                || doctor.getStatus() == null || doctor.getStatus() != 1) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "所选医生不可用");
        }
        return doctor;
    }

    private void requireCaseRole(CurrentUserVO user) {
        if (user == null || (user.getRoleCode() != UserRole.USER && user.getRoleCode() != UserRole.DOCTOR)) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "当前角色不能访问病例");
        }
    }

    private CaseEntity requireActiveCase(Integer caseId) {
        CaseEntity medicalCase = caseMapper.selectById(caseId);
        if (medicalCase == null || medicalCase.getStatus() == CaseStatus.DELETED) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
        }
        return medicalCase;
    }

    private void requirePatientOwns(CaseEntity medicalCase, CurrentUserVO user) {
        if (user == null || user.getRoleCode() != UserRole.USER) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "仅患者本人可以执行此操作");
        }
        PatientProfileEntity profile = patientProfiles.getOrCreateAccountProfile(user.getId());
        if (!profile.getId().equals(medicalCase.getPatientId())) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
        }
    }

    private void requireCaseAccessForMutation(CaseEntity medicalCase, CurrentUserVO user) {
        requireCaseRole(user);
        if (user.getRoleCode() == UserRole.USER) {
            requirePatientOwns(medicalCase, user);
        } else if (!user.getId().equals(medicalCase.getAssignedDoctorId())) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
        }
    }

    private String generateCaseNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(100, 1000);
        return "C" + timestamp + random;
    }
}
