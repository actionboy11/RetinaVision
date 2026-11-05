package com.example.retinavision.pojo.clinical.vo;

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
