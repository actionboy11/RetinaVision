package com.example.retinavision.service.impl;

import com.example.retinavision.ai.AiHealthClient;
import com.example.retinavision.ai.dto.AiHealthResponse;
import com.example.retinavision.pojo.VO.QueueStatisticsVO;
import com.example.retinavision.pojo.VO.SystemStatusVO;
import com.example.retinavision.pojo.VO.TaskStatisticsVO;
import com.example.retinavision.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemStatusServiceImplTest {

    @Mock private AiHealthClient aiHealthClient;
    @Mock private StatisticsService statisticsService;

    @Test
    void reportsUpWhenAiQueueAndDatabaseAreAvailable() {
        when(aiHealthClient.checkHealth()).thenReturn(upAi(false));
        when(statisticsService.getTaskStatistics()).thenReturn(taskStatistics());
        when(statisticsService.getQueueStatistics()).thenReturn(queueStatistics());
        SystemStatusServiceImpl service = new SystemStatusServiceImpl(aiHealthClient, statisticsService);

        SystemStatusVO status = service.getStatus();

        assertThat(status.getOverallStatus()).isEqualTo("UP");
        assertThat(status.getAi().isReachable()).isTrue();
        assertThat(status.getQueue().getStatus()).isEqualTo("UP");
        assertThat(status.getTasks().getRetryingCount()).isEqualTo(2L);
        assertThat(status.getCheckedAt()).isNotNull();
    }

    @Test
    void reportsDegradedAndPreservesDataWhenAiIsDown() {
        when(aiHealthClient.checkHealth()).thenReturn(AiHealthResponse.builder()
                .status("DOWN")
                .reachable(false)
                .lastError("AI 健康检查失败")
                .build());
        when(statisticsService.getTaskStatistics()).thenReturn(taskStatistics());
        when(statisticsService.getQueueStatistics()).thenReturn(queueStatistics());
        SystemStatusServiceImpl service = new SystemStatusServiceImpl(aiHealthClient, statisticsService);

        SystemStatusVO status = service.getStatus();

        assertThat(status.getOverallStatus()).isEqualTo("DEGRADED");
        assertThat(status.getAi().getStatus()).isEqualTo("DOWN");
        assertThat(status.getQueue().getMessageReadyCount()).isEqualTo(3L);
        assertThat(status.getTasks().getSuccessRate()).isEqualTo(0.9D);
    }

    @Test
    void reportsQueueDownInsteadOfLosingAiAndTaskStatus() {
        when(aiHealthClient.checkHealth()).thenReturn(upAi(true));
        when(statisticsService.getTaskStatistics()).thenReturn(taskStatistics());
        when(statisticsService.getQueueStatistics()).thenThrow(new IllegalStateException("Rabbit unavailable"));
        SystemStatusServiceImpl service = new SystemStatusServiceImpl(aiHealthClient, statisticsService);

        SystemStatusVO status = service.getStatus();

        assertThat(status.getOverallStatus()).isEqualTo("DEGRADED");
        assertThat(status.getAi().isBusy()).isTrue();
        assertThat(status.getQueue().getStatus()).isEqualTo("DOWN");
        assertThat(status.getQueue().getError()).contains("RabbitMQ 队列状态获取失败");
        assertThat(status.getTasks().getTotalTaskCount()).isEqualTo(10L);
    }

    @Test
    void doesNotHidePersistentDatabaseStatisticsFailure() {
        when(aiHealthClient.checkHealth()).thenReturn(upAi(false));
        when(statisticsService.getTaskStatistics()).thenThrow(new IllegalStateException("Database unavailable"));
        SystemStatusServiceImpl service = new SystemStatusServiceImpl(aiHealthClient, statisticsService);

        assertThatThrownBy(service::getStatus)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database unavailable");
    }

    private AiHealthResponse upAi(boolean busy) {
        return AiHealthResponse.builder()
                .status("UP")
                .reachable(true)
                .ready(true)
                .modelLoaded(true)
                .modelName("FSCNet")
                .modelVersion("v1")
                .device("cpu")
                .busy(busy)
                .build();
    }

    private TaskStatisticsVO taskStatistics() {
        return TaskStatisticsVO.builder()
                .totalTaskCount(10L)
                .successRate(0.9D)
                .averageProcessingTimeMs(120L)
                .todayFailedCount(1L)
                .retryingCount(2L)
                .build();
    }

    private QueueStatisticsVO queueStatistics() {
        return QueueStatisticsVO.builder()
                .queueName("retina.analysis.task.queue")
                .messageReadyCount(3L)
                .messageUnackedCount(1L)
                .consumerCount(1L)
                .deadLetterCount(0L)
                .build();
    }
}
