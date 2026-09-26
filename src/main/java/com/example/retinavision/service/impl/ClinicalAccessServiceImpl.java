package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.PatientProfileMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.PatientProfileEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.service.ClinicalAccessService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ClinicalAccessServiceImpl implements ClinicalAccessService {
    private final CaseMapper caseMapper;
    private final ImageMapper imageMapper;
    private final TaskMapper taskMapper;
    private final AnalysisResultMapper resultMapper;
    private final PatientProfileMapper patientProfileMapper;

    public ClinicalAccessServiceImpl(CaseMapper caseMapper,
                                     ImageMapper imageMapper,
                                     TaskMapper taskMapper,
                                     AnalysisResultMapper resultMapper,
                                     PatientProfileMapper patientProfileMapper) {
        this.caseMapper = caseMapper;
        this.imageMapper = imageMapper;
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.patientProfileMapper = patientProfileMapper;
    }

    // 检验当前用户是否有临床访问权限
       @Override
    public void assertClinicalRole(CurrentUserVO user) {
        if (user == null) {
            throw new BaseException(ErrorMessageSignal.UNAUTHORIZED, "请先登录");
        }
        if (user.getRoleCode() != UserRole.USER && user.getRoleCode() != UserRole.DOCTOR) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "当前角色不能访问临床数据");
        }
    }

    // 检验当前用户是否有权限访问指定病例详情
    @Override
    public void assertCanAccessCase(CurrentUserVO user, Long caseId) {
        assertClinicalRole(user);
        CaseEntity medicalCase = caseMapper.selectById(caseId);
        if (medicalCase == null || medicalCase.getDeletedAt() != null) {
            notFound();
        }
        boolean allowed = switch (user.getRoleCode()) {
            case USER -> {
                PatientProfileEntity profile = patientProfileMapper.selectById(medicalCase.getPatientId());
                yield profile != null && Objects.equals(profile.getAccountUserId(), user.getId());
            }
            case DOCTOR -> Objects.equals(medicalCase.getAssignedDoctorId(), user.getId());
            case ADMIN, RESEARCHER -> false;
        };
        if (!allowed) {
            notFound();
        }
    }

    // 检验当前用户是否有权限访问指定图像详情
    @Override
    public void assertCanAccessImage(CurrentUserVO user, Long imageId) {
        assertClinicalRole(user);
        ImageFileEntity image = imageMapper.selectById(imageId);
        if (image == null || image.getDeletedAt() != null) {
            notFound();
        }
        assertCanAccessCase(user, image.getCaseId());
    }
    // 检验当前用户是否有权限访问指定任务详情
    @Override
    public void assertCanAccessTask(CurrentUserVO user, Long taskId) {
        assertClinicalRole(user);
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            notFound();
        }
        assertCanAccessCase(user, task.getCaseId());
    }

    // 检验当前用户是否有权限访问指定分析结果详情
    @Override
    public void assertCanAccessResult(CurrentUserVO user, Long resultId) {
        assertClinicalRole(user);
        AnalysisResultEntity result = resultMapper.selectById(resultId);
        if (result == null) {
            notFound();
        }
        assertCanAccessTask(user, result.getTaskId());
    }

    @Override
    public void assertCanModifyCase(CurrentUserVO user, Long caseId) {
        assertCanAccessCase(user, caseId);
        CaseEntity medicalCase = caseMapper.selectById(caseId);
        if (user.getRoleCode() == UserRole.USER
                && medicalCase.getWorkflowStatus() != CaseWorkflowStatus.DRAFT) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "病例提交后不能修改图像");
        }
        if (user.getRoleCode() == UserRole.DOCTOR
                && (medicalCase.getWorkflowStatus() == CaseWorkflowStatus.COMPLETED
                || medicalCase.getWorkflowStatus() == CaseWorkflowStatus.WITHDRAWN)) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "当前病例状态不能修改图像");
        }
    }

    @Override
    public void assertCanModifyImage(CurrentUserVO user, Long imageId) {
        assertCanAccessImage(user, imageId);
        ImageFileEntity image = imageMapper.selectById(imageId);
        assertCanModifyCase(user, image.getCaseId());
    }

    private void notFound() {
        throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
    }
}
