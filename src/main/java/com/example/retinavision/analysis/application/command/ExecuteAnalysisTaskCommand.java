package com.example.retinavision.analysis.application.command;

public record ExecuteAnalysisTaskCommand(long taskId, String traceId) {

    public ExecuteAnalysisTaskCommand {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId 必须为正数");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空");
        }
    }
}
