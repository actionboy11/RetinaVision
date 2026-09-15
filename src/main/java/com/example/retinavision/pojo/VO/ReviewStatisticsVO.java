package com.example.retinavision.pojo.VO;

import java.util.Map;

public record ReviewStatisticsVO(
        long totalReviewCount,
        Map<String, Long> reviewDistribution,
        double approvedRate,
        double needsChangeRate,
        double rejectedRate,
        long signedReportCount,
        long approvedUnsignedCount
) {
}
