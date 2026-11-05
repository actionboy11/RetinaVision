package com.example.retinavision.pojo.cases.vo;

import com.example.retinavision.enumeration.cases.EyeSide;
import com.example.retinavision.enumeration.imaging.ImageQualityStatus;
import com.example.retinavision.enumeration.reporting.ReportStatus;
import com.example.retinavision.enumeration.clinical.ReviewStatus;
import com.example.retinavision.enumeration.analysis.TaskStatus;
import com.example.retinavision.enumeration.analysis.TaskType;

import java.time.LocalDateTime;
import java.util.List;

public record CaseAnalysisTimelineVO(
        Long caseId,
        EyeSide eyeSide,
        List<Item> items
) {
    public record Item(
            Long taskId,
            String taskNo,
            TaskType taskType,
            TaskStatus taskStatus,
            Long imageFileId,
            Long resultId,
            Double qualityScore,
            ImageQualityStatus qualityStatus,
            Long qualityTaskId,
            Long qualityResultId,
            LocalDateTime qualityCheckedAt,
            Double vesselAreaRatio,
            String modelName,
            String modelVersion,
            Integer processingTimeMs,
            ReviewStatus reviewStatus,
            ReportStatus reportStatus,
            Integer reportVersion,
            LocalDateTime submittedAt,
            LocalDateTime finishedAt,
            LocalDateTime resultCreatedAt
    ) {
    }
}
