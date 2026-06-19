package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mq.AnalysisTaskMessage;
import com.example.retinavision.mq.AnalysisTaskMessagePublisher;
import com.example.retinavision.mq.RabbitMqProperties;
import com.example.retinavision.pojo.Entity.LogEntity;
import com.example.retinavision.service.AnalysisTaskRetryService;
import com.example.retinavision.service.DeadLetterRecoveryService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DeadLetterRecoveryServiceImpl implements DeadLetterRecoveryService {

    private static final int MAX_RECOVERY_BATCH = 100;

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties properties;
    private final TaskMapper taskMapper;
    private final LogMapper logMapper;
    private final AnalysisTaskMessagePublisher publisher;
    private final AnalysisTaskRetryService retryService;

    public DeadLetterRecoveryServiceImpl(RabbitTemplate rabbitTemplate,
                                         RabbitMqProperties properties,
                                         TaskMapper taskMapper,
                                         LogMapper logMapper,
                                         AnalysisTaskMessagePublisher publisher,
                                         AnalysisTaskRetryService retryService) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.publisher = publisher;
        this.retryService = retryService;
    }

    @Override
    public int recover(Integer operatorId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECOVERY_BATCH));
        int recovered = 0;
        for (int index = 0; index < safeLimit; index++) {
            Message rawMessage = rabbitTemplate.receive(properties.getAnalysisDeadLetterQueue());
            if (rawMessage == null) {
                break;
            }
            if (recoverOne(rawMessage, operatorId)) {
                recovered++;
            } else {
                // 本条无法恢复时放回死信并停止本批次，避免立即取回同一条消息形成死循环。
                restoreDeadLetter(rawMessage);
                break;
            }
        }
        return recovered;
    }

    private boolean recoverOne(Message rawMessage, Integer operatorId) {
        try {
            Object converted = rabbitTemplate.getMessageConverter().fromMessage(rawMessage);
            if (!(converted instanceof AnalysisTaskMessage message) || message.getTaskId() == null) {
                return false;
            }

            LocalDateTime now = LocalDateTime.now();
            // 有限重试与死信恢复功能完善：管理员恢复时重置自动重试次数，让任务重新进入完整的三次重试周期。
            int updated = taskMapper.prepareDeadLetterRecovery(message.getTaskId(), operatorId, now);
            if (updated == 0) {
                return false;
            }
            try {
                publisher.publish(message);
                logMapper.insert(LogEntity.builder()
                        .taskId(message.getTaskId())
                        .fromStatus(TaskStatus.FAILED)
                        .toStatus(TaskStatus.RETRYING)
                        .message("管理员从死信队列恢复任务，已重置自动重试次数")
                        .operatorType("USER")
                        .operatorId(operatorId)
                        .createdAt(now)
                        .build());
                return true;
            } catch (Exception exception) {
                retryService.markRetryPublishFailed(message.getTaskId(), "死信恢复消息重新投递失败");
                return false;
            }
        } catch (Exception exception) {
            return false;
        }
    }

    private void restoreDeadLetter(Message rawMessage) {
        rabbitTemplate.send(
                properties.getAnalysisDeadLetterExchange(),
                properties.getAnalysisDeadLetterRoutingKey(),
                rawMessage
        );
    }
}
