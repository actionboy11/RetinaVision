package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.service.AgentClinicalReferenceService;
import org.springframework.stereotype.Service;

@Service
public class AgentClinicalReferenceServiceImpl implements AgentClinicalReferenceService {
    private final CaseMapper caseMapper;
    private final TaskMapper taskMapper;

    public AgentClinicalReferenceServiceImpl(CaseMapper caseMapper, TaskMapper taskMapper) {
        this.caseMapper = caseMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    public CaseListItemVO resolveCase(String reference, CurrentUserVO doctor) {
        assertDoctor(doctor);
        String normalized = normalize(reference);
        CaseListItemVO result = caseMapper.findAssignedCaseByReference(
                normalized, numericId(normalized), doctor.getId());
        if (result == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
        }
        return result;
    }

    @Override
    public TaskEntity resolveTask(String reference, CurrentUserVO doctor) {
        assertDoctor(doctor);
        String normalized = normalize(reference);
        TaskEntity result = taskMapper.findAssignedTaskByReference(
                normalized, numericId(normalized), doctor.getId());
        if (result == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
        }
        return result;
    }

    private void assertDoctor(CurrentUserVO user) {
        if (user == null || user.getRoleCode() != UserRole.DOCTOR) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权访问临床病例工具");
        }
    }

    private String normalize(String reference) {
        if (reference == null || reference.trim().isEmpty() || reference.length() > 64) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "业务编号不能为空且不能超过 64 个字符");
        }
        return reference.trim();
    }

    private Long numericId(String reference) {
        try {
            return Long.valueOf(reference);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
