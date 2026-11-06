package com.example.retinavision.pojo.governance.vo;

import java.util.Map;

public record QualityControlOverviewVO(
        long taskTotalCount,
        double successRate,
        double failedRate,
        long averageProcessingTimeMs,
        long retryTaskCount,
        Map<String, Long> qualityDistribution,
        Map<String, Long> reviewDistribution,
        long signedReportCount,
        long approvedUnsignedCount,
        long vesselResultCount,
        double averageVesselAreaRatio,
        long abnormalLowVesselRatioCount,
        long abnormalHighVesselRatioCount
) {
}
