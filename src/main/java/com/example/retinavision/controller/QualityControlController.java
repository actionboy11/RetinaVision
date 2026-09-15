package com.example.retinavision.controller;

import com.example.retinavision.pojo.VO.ModelPerformanceItemVO;
import com.example.retinavision.pojo.VO.QualityControlOverviewVO;
import com.example.retinavision.pojo.VO.ReviewStatisticsVO;
import com.example.retinavision.pojo.VO.RiskAlertItemVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.QualityControlService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/quality-control")
public class QualityControlController {
    private final QualityControlService service;

    public QualityControlController(QualityControlService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Result<QualityControlOverviewVO> overview() {
        return Result.success(service.overview());
    }

    @GetMapping("/model-performance")
    public Result<List<ModelPerformanceItemVO>> modelPerformance() {
        return Result.success(service.modelPerformance());
    }

    @GetMapping("/review-statistics")
    public Result<ReviewStatisticsVO> reviewStatistics() {
        return Result.success(service.reviewStatistics());
    }

    @GetMapping("/risk-alerts")
    public Result<List<RiskAlertItemVO>> riskAlerts() {
        return Result.success(service.riskAlerts());
    }
}
