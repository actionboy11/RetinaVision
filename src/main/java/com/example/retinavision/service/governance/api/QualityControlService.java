package com.example.retinavision.service.governance.api;

import com.example.retinavision.pojo.operations.vo.ModelPerformanceItemVO;
import com.example.retinavision.pojo.governance.vo.QualityControlOverviewVO;
import com.example.retinavision.pojo.clinical.vo.ReviewStatisticsVO;
import com.example.retinavision.pojo.governance.vo.RiskAlertItemVO;

import java.util.List;

public interface QualityControlService {
    QualityControlOverviewVO overview();

    List<ModelPerformanceItemVO> modelPerformance();

    ReviewStatisticsVO reviewStatistics();

    List<RiskAlertItemVO> riskAlerts();
}
