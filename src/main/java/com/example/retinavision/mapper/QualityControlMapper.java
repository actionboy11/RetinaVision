package com.example.retinavision.mapper;

import com.example.retinavision.pojo.VO.ModelPerformanceItemVO;
import com.example.retinavision.pojo.VO.RiskAlertItemVO;

import java.util.List;

public interface QualityControlMapper {
    OverviewRow selectOverview();

    List<CountRow> selectQualityDistribution();

    List<CountRow> selectReviewDistribution();

    List<ModelPerformanceItemVO> selectModelPerformance();

    List<RiskAlertItemVO> selectRiskAlerts();

    record CountRow(String name, Long count) {
    }

    record OverviewRow(
            Long taskTotalCount,
            Double successRate,
            Double failedRate,
            Long averageProcessingTimeMs,
            Long retryTaskCount,
            Long signedReportCount,
            Long approvedUnsignedCount,
            Long vesselResultCount,
            Double averageVesselAreaRatio,
            Long abnormalLowVesselRatioCount,
            Long abnormalHighVesselRatioCount
    ) {
    }
}
