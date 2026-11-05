package com.example.retinavision.mapper.governance;

import com.example.retinavision.pojo.operations.vo.ModelPerformanceItemVO;
import com.example.retinavision.pojo.governance.vo.RiskAlertItemVO;

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
