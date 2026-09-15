package com.example.retinavision.service.impl;

import com.example.retinavision.mapper.QualityControlMapper;
import com.example.retinavision.pojo.VO.ModelPerformanceItemVO;
import com.example.retinavision.pojo.VO.QualityControlOverviewVO;
import com.example.retinavision.pojo.VO.ReviewStatisticsVO;
import com.example.retinavision.pojo.VO.RiskAlertItemVO;
import com.example.retinavision.service.QualityControlService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QualityControlServiceImpl implements QualityControlService {
    private static final List<String> QUALITY_STATUSES = List.of("NOT_CHECKED", "CHECKING", "PASS", "WARNING", "FAIL", "ERROR");
    private static final List<String> REVIEW_STATUSES = List.of("PENDING", "CHANGES_REQUESTED", "APPROVED", "REJECTED");
    private static final Map<String, Integer> RISK_ORDER = Map.of("CRITICAL", 0, "WARNING", 1, "INFO", 2);

    private final QualityControlMapper mapper;

    public QualityControlServiceImpl(QualityControlMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public QualityControlOverviewVO overview() {
        QualityControlMapper.OverviewRow base = mapper.selectOverview();
        if (base == null) {
            base = new QualityControlMapper.OverviewRow(0L, 0.0, 0.0, 0L, 0L, 0L, 0L, 0L, 0.0, 0L, 0L);
        }
        return new QualityControlOverviewVO(
                safeLong(base.taskTotalCount()),
                safeDouble(base.successRate()),
                safeDouble(base.failedRate()),
                safeLong(base.averageProcessingTimeMs()),
                safeLong(base.retryTaskCount()),
                countMap(mapper.selectQualityDistribution(), QUALITY_STATUSES),
                countMap(mapper.selectReviewDistribution(), REVIEW_STATUSES),
                safeLong(base.signedReportCount()),
                safeLong(base.approvedUnsignedCount()),
                safeLong(base.vesselResultCount()),
                safeDouble(base.averageVesselAreaRatio()),
                safeLong(base.abnormalLowVesselRatioCount()),
                safeLong(base.abnormalHighVesselRatioCount())
        );
    }

    @Override
    public List<ModelPerformanceItemVO> modelPerformance() {
        List<ModelPerformanceItemVO> items = mapper.selectModelPerformance();
        return items == null ? List.of() : items;
    }

    @Override
    public ReviewStatisticsVO reviewStatistics() {
        QualityControlOverviewVO overview = overview();
        Map<String, Long> distribution = overview.reviewDistribution();
        long total = distribution.values().stream().mapToLong(Long::longValue).sum();
        return new ReviewStatisticsVO(
                total,
                distribution,
                rate(distribution.get("APPROVED"), total),
                rate(distribution.get("CHANGES_REQUESTED"), total),
                rate(distribution.get("REJECTED"), total),
                overview.signedReportCount(),
                overview.approvedUnsignedCount()
        );
    }

    @Override
    public List<RiskAlertItemVO> riskAlerts() {
        List<RiskAlertItemVO> alerts = mapper.selectRiskAlerts();
        if (alerts == null || alerts.isEmpty()) {
            return List.of();
        }
        return alerts.stream()
                .sorted(Comparator
                        .comparingInt((RiskAlertItemVO item) -> RISK_ORDER.getOrDefault(item.level(), 9))
                        .thenComparing(RiskAlertItemVO::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private Map<String, Long> countMap(List<QualityControlMapper.CountRow> rows, List<String> defaults) {
        Map<String, Long> result = new LinkedHashMap<>();
        defaults.forEach(key -> result.put(key, 0L));
        if (rows != null) {
            for (QualityControlMapper.CountRow row : rows) {
                if (row != null && row.name() != null) {
                    result.put(row.name(), row.count() == null ? 0L : row.count());
                }
            }
        }
        return result;
    }

    private double rate(Long count, long total) {
        if (total <= 0 || count == null) {
            return 0;
        }
        return (double) count / total;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }
}
