package com.example.retinavision.pojo.analysis.vo;

import com.example.retinavision.enumeration.cases.EyeSide;
import com.example.retinavision.enumeration.imaging.ImageQualityStatus;
import com.example.retinavision.enumeration.reporting.ReportStatus;
import com.example.retinavision.enumeration.clinical.ReviewStatus;
import com.example.retinavision.enumeration.analysis.TaskType;

import java.time.LocalDateTime;
import java.util.List;

public record AnalysisResultComparisonVO(
        Long caseId,
        EyeSide eyeSide,
        TaskType taskType,
        Snapshot baseline,
        Snapshot target,
        Double qualityScoreDelta,
        Double vesselAreaRatioDelta,
        boolean modelChanged,
        boolean reviewStatusChanged,
        boolean reportStatusChanged,
        List<String> notes
) {
    public record Snapshot(
            Long resultId,
            Long taskId,
            Long imageFileId,
            Double qualityScore,
            ImageQualityStatus qualityStatus,
            Double vesselAreaRatio,
            String modelName,
            String modelVersion,
            ReviewStatus reviewStatus,
            ReportStatus reportStatus,
            LocalDateTime finishedAt,
            LocalDateTime resultCreatedAt
    ) {
    }
}
