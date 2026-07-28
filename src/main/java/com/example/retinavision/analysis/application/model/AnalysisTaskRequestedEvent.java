package com.example.retinavision.analysis.application.model;

import com.example.retinavision.analysis.domain.model.AnalysisTaskType;

import java.time.LocalDateTime;

public record AnalysisTaskRequestedEvent(
        long taskId,
        String taskNo,
        long caseId,
        long imageFileId,
        AnalysisTaskType taskType,
        int priority,
        int submittedBy,
        LocalDateTime submittedAt) {
}
