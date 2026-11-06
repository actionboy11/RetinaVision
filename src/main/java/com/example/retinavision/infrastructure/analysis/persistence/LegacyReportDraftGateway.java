package com.example.retinavision.infrastructure.analysis.persistence;

import com.example.retinavision.port.analysis.ReportDraftPort;
import com.example.retinavision.mapper.analysis.TaskMapper;
import com.example.retinavision.pojo.analysis.entity.TaskEntity;
import com.example.retinavision.service.reporting.AnalysisReportService;

public final class LegacyReportDraftGateway implements ReportDraftPort {

    private final TaskMapper taskMapper;
    private final AnalysisReportService reportService;

    public LegacyReportDraftGateway(
            TaskMapper taskMapper,
            AnalysisReportService reportService) {
        this.taskMapper = taskMapper;
        this.reportService = reportService;
    }

    @Override
    public void ensureDraft(long resultId, long taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || task.getSubmittedBy() == null) {
            throw new IllegalStateException("任务提交人不存在");
        }
        reportService.getOrCreateDraft(resultId, task.getSubmittedBy());
    }
}
