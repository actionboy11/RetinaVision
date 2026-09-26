package com.example.retinavision.service.impl;

import com.example.retinavision.mapper.QualityControlMapper;
import com.example.retinavision.pojo.VO.ModelPerformanceItemVO;
import com.example.retinavision.pojo.VO.QualityControlOverviewVO;
import com.example.retinavision.pojo.VO.RiskAlertItemVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualityControlServiceImplTest {
    private final QualityControlMapper mapper = mock(QualityControlMapper.class);

    @Test
    void overviewReturnsZeroDefaultsWhenDatabaseHasNoRows() {
        when(mapper.selectOverview()).thenReturn(null);
        when(mapper.selectQualityDistribution()).thenReturn(List.of());
        when(mapper.selectReviewDistribution()).thenReturn(List.of());

        QualityControlOverviewVO overview = service().overview();

        assertThat(overview.taskTotalCount()).isZero();
        assertThat(overview.successRate()).isZero();
        assertThat(overview.failedRate()).isZero();
        assertThat(overview.averageProcessingTimeMs()).isZero();
        assertThat(overview.qualityDistribution()).containsEntry("PASS", 0L);
        assertThat(overview.reviewDistribution()).containsEntry("APPROVED", 0L);
    }

    @Test
    void overviewCombinesTaskQualityReviewAndReportMetrics() {
        QualityControlMapper.OverviewRow base = new QualityControlMapper.OverviewRow(
                10L, 0.7, 0.2, 1200L, 3L, 4L, 2L,
                8L, 0.092, 1L, 2L
        );
        when(mapper.selectOverview()).thenReturn(base);
        when(mapper.selectQualityDistribution()).thenReturn(List.of(
                new QualityControlMapper.CountRow("PASS", 6L),
                new QualityControlMapper.CountRow("FAIL", 2L)
        ));
        when(mapper.selectReviewDistribution()).thenReturn(List.of(
                new QualityControlMapper.CountRow("APPROVED", 5L),
                new QualityControlMapper.CountRow("CHANGES_REQUESTED", 1L)
        ));

        QualityControlOverviewVO overview = service().overview();

        assertThat(overview.taskTotalCount()).isEqualTo(10L);
        assertThat(overview.retryTaskCount()).isEqualTo(3L);
        assertThat(overview.signedReportCount()).isEqualTo(4L);
        assertThat(overview.approvedUnsignedCount()).isEqualTo(2L);
        assertThat(overview.qualityDistribution()).containsEntry("PASS", 6L).containsEntry("WARNING", 0L);
        assertThat(overview.reviewDistribution()).containsEntry("APPROVED", 5L).containsEntry("REJECTED", 0L);
    }

    @Test
    void modelPerformancePassesThroughGroupedModelStatistics() {
        ModelPerformanceItemVO item = new ModelPerformanceItemVO(
                "FSCNet", "v1", "VESSEL_SEGMENTATION", 5L, 0.8,
                900L, 0.086, 0.75, 0.25, 0.2
        );
        when(mapper.selectModelPerformance()).thenReturn(List.of(item));

        assertThat(service().modelPerformance()).containsExactly(item);
    }

    @Test
    void riskAlertsMergeMapperResultsInSeverityOrder() {
        RiskAlertItemVO warning = new RiskAlertItemVO("WARNING", "血管面积比例异常", 10L, "T10", 100L,
                1L, "VESSEL_SEGMENTATION", "SUCCESS", null);
        RiskAlertItemVO critical = new RiskAlertItemVO("CRITICAL", "医生审核拒绝", 11L, "T11", 101L,
                1L, "VESSEL_SEGMENTATION", "SUCCESS", null);
        when(mapper.selectRiskAlerts()).thenReturn(List.of(warning, critical));

        assertThat(service().riskAlerts()).extracting(RiskAlertItemVO::level)
                .containsExactly("CRITICAL", "WARNING");
    }

    private QualityControlServiceImpl service() {
        return new QualityControlServiceImpl(mapper);
    }
}
