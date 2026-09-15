package com.example.retinavision.pojo.VO;

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
