package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;

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
