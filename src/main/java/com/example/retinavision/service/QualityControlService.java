package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.ModelPerformanceItemVO;
import com.example.retinavision.pojo.VO.QualityControlOverviewVO;
import com.example.retinavision.pojo.VO.ReviewStatisticsVO;
import com.example.retinavision.pojo.VO.RiskAlertItemVO;

import java.util.List;

public interface QualityControlService {
    QualityControlOverviewVO overview();

    List<ModelPerformanceItemVO> modelPerformance();

    ReviewStatisticsVO reviewStatistics();

    List<RiskAlertItemVO> riskAlerts();
}
