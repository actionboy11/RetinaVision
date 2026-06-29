package com.example.retinavision.service.impl;

import com.example.retinavision.ai.AiHealthClient;
import com.example.retinavision.ai.dto.AiHealthResponse;
import com.example.retinavision.pojo.VO.QueueStatisticsVO;
import com.example.retinavision.pojo.VO.SystemStatusVO;
import com.example.retinavision.pojo.VO.TaskStatisticsVO;
import com.example.retinavision.service.StatisticsService;
import com.example.retinavision.service.SystemStatusService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
// 系统状态服务实现类
public class SystemStatusServiceImpl implements SystemStatusService {

    private final AiHealthClient aiHealthClient;
    private final StatisticsService statisticsService;

    public SystemStatusServiceImpl(AiHealthClient aiHealthClient, StatisticsService statisticsService) {
        this.aiHealthClient = aiHealthClient;
        this.statisticsService = statisticsService;
    }

    @Override
    public SystemStatusVO getStatus() {
        AiHealthResponse ai = getAiStatus();
        // 数据库是长期业务指标的事实来源；读取失败时不伪造健康数据。
        TaskStatisticsVO tasks = statisticsService.getTaskStatistics();
        SystemStatusVO.QueueStatus queue = getQueueStatus();
        boolean aiUp = ai.isReachable() && "UP".equals(ai.getStatus());
        boolean queueUp = "UP".equals(queue.getStatus());
        return SystemStatusVO.builder()
                .overallStatus(aiUp && queueUp ? "UP" : "DEGRADED")
                .checkedAt(OffsetDateTime.now())
                .ai(ai)
                .queue(queue)
                .tasks(tasks)
                .build();
    }

    private AiHealthResponse getAiStatus() {
        try {
            // 检查 AI 服务的健康状态
            AiHealthResponse status = aiHealthClient.checkHealth();
            if (status != null) {
                return status;
            }
        } catch (Exception ignored) {
            // 健康探测只影响 AI 组件状态，不应阻断数据库和队列状态返回。
        }
        return AiHealthResponse.builder()
                .status("DOWN")
                .reachable(false)
                .ready(false)
                .lastError("AI 健康检查失败")
                .build();
    }

    private SystemStatusVO.QueueStatus getQueueStatus() {
        try {
            QueueStatisticsVO queue = statisticsService.getQueueStatistics();
            return SystemStatusVO.QueueStatus.builder()
                    .status("UP")
                    .queueName(queue.getQueueName())
                    .messageReadyCount(queue.getMessageReadyCount())
                    .messageUnackedCount(queue.getMessageUnackedCount())
                    .consumerCount(queue.getConsumerCount())
                    .deadLetterCount(queue.getDeadLetterCount())
                    .build();
        } catch (Exception exception) {
            return SystemStatusVO.QueueStatus.builder()
                    .status("DOWN")
                    .error("RabbitMQ 队列状态获取失败：" + exception.getClass().getSimpleName())
                    .build();
        }
    }
}
