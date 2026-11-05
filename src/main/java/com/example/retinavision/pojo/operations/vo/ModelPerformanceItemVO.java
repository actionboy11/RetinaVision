package com.example.retinavision.pojo.operations.vo;

public record ModelPerformanceItemVO(
        String modelName,
        String modelVersion,
        String resultType,
        long resultCount,
        double successRate,
        long averageProcessingTimeMs,
        double averageVesselAreaRatio,
        double reviewApprovedRate,
        double reviewNeedsChangeRate,
        double failedRate
) {
}
