package com.example.retinavision.service.impl;

import com.example.retinavision.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.agent.DoctorTaskSearchCriteria;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.DoctorAgentQueryMapper;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.DoctorWorkloadVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskDetailVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskSummaryVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.DoctorAgentQueryService;
import org.springframework.stereotype.Service;

@Service
public class DoctorAgentQueryServiceImpl implements DoctorAgentQueryService {
    private static final int PAGE_SIZE = 10;
    private static final int SUMMARY_LIMIT = 200;
    private final DoctorAgentQueryMapper mapper;

    public DoctorAgentQueryServiceImpl(DoctorAgentQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DoctorWorkloadVO getClinicalWorkload(CurrentUserVO doctor) {
        requireDoctor(doctor);
        DoctorWorkloadVO value = mapper.selectWorkload(doctor.getId());
        return value == null ? new DoctorWorkloadVO(0, 0, 0, 0, 0, 0) : value;
    }

    @Override
    public PageResult<DoctorAgentCaseSummaryVO> searchAssignedCases(DoctorCaseSearchCriteria criteria,
                                                                    Integer page,
                                                                    Integer pageSize,
                                                                    CurrentUserVO doctor) {
        requireDoctor(doctor);
        DoctorCaseSearchCriteria safeCriteria = criteria == null
                ? new DoctorCaseSearchCriteria(null, null) : criteria;
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? PAGE_SIZE : Math.min(pageSize, PAGE_SIZE);
        long total = mapper.countAssignedCases(doctor.getId(), safeCriteria);
        var records = mapper.selectAssignedCases(doctor.getId(), safeCriteria,
                (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(records, total, safePage, safeSize);
    }

    @Override
    public PageResult<DoctorAgentTaskSummaryVO> searchTasks(DoctorTaskSearchCriteria criteria,
                                                            Integer page,
                                                            Integer pageSize,
                                                            CurrentUserVO doctor) {
        requireDoctor(doctor);
        DoctorTaskSearchCriteria safeCriteria = criteria == null
                ? new DoctorTaskSearchCriteria(null, null, null) : criteria;
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? PAGE_SIZE : Math.min(pageSize, PAGE_SIZE);
        long total = mapper.countTasks(doctor.getId(), safeCriteria);
        var records = mapper.selectTasks(doctor.getId(), safeCriteria, (safePage - 1) * safeSize, safeSize);
        records.forEach(task -> task.setErrorSummary(sanitizeSummary(task.getErrorSummary())));
        return new PageResult<>(records, total, safePage, safeSize);
    }

    @Override
    public DoctorAgentTaskDetailVO getTaskDetail(String taskReference, CurrentUserVO doctor) {
        requireDoctor(doctor);
        String reference = taskReference == null ? "" : taskReference.trim();
        Long taskId = parseTaskId(reference);
        DoctorAgentTaskDetailVO detail = mapper.selectTaskDetail(doctor.getId(), reference, taskId);
        if (detail == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
        }
        detail.setErrorSummary(sanitizeSummary(detail.getErrorSummary()));
        var logs = mapper.selectTaskLogs(doctor.getId(), detail.getTaskId());
        logs.forEach(log -> log.setMessage(sanitizeSummary(log.getMessage())));
        detail.setLogs(logs);
        return detail;
    }

    private Long parseTaskId(String reference) {
        try {
            return reference.matches("\\d+") ? Long.valueOf(reference) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String sanitizeSummary(String value) {
        if (value == null) return null;
        String singleLine = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return singleLine.length() <= SUMMARY_LIMIT ? singleLine : singleLine.substring(0, SUMMARY_LIMIT);
    }

    private void requireDoctor(CurrentUserVO doctor) {
        if (doctor == null || doctor.getRoleCode() != UserRole.DOCTOR) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权使用医生病例查询能力");
        }
    }
}
