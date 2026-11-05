package com.example.retinavision.pojo.analysis.event;
import com.example.retinavision.enumeration.analysis.TaskType;

import com.example.retinavision.pojo.analysis.domain.AnalysisTaskType;

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
