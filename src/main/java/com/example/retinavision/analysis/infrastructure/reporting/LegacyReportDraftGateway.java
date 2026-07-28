package com.example.retinavision.analysis.infrastructure.reporting;

import com.example.retinavision.analysis.application.port.out.ReportDraftPort;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.service.AnalysisReportService;

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
