package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mq.RabbitMqProperties;
import com.example.retinavision.pojo.Entity.LogEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.service.AnalysisTaskRetryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AnalysisTaskRetryServiceImpl implements AnalysisTaskRetryService {

    private static final int RETRY_DELAY_SECONDS = 10;

    private final TaskMapper taskMapper;
    private final LogMapper logMapper;
    private final RabbitMqProperties properties;

    public AnalysisTaskRetryServiceImpl(TaskMapper taskMapper,
                                        LogMapper logMapper,
                                        RabbitMqProperties properties) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    public Integer prepareAutomaticRetry(Long taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() != TaskStatus.FAILED) {
            return null;
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int taskMaxRetryCount = task.getMaxRetryCount() == null ? 0 : task.getMaxRetryCount();
        int configuredMaxRetryCount = properties.getAnalysisMaxAutoRetries() == null
                ? 3 : properties.getAnalysisMaxAutoRetries();
        int maxRetryCount = Math.min(taskMaxRetryCount, configuredMaxRetryCount);
        if (retryCount >= maxRetryCount) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        // 有限重试与死信恢复功能完善：用状态和旧重试次数做原子条件，防止重复消息同时占用同一次重试机会。
        int updated = taskMapper.prepareAutomaticRetry(taskId, retryCount, now);
        if (updated == 0) {
            return null;
        }

        int nextRetryCount = retryCount + 1;
        logMapper.insert(LogEntity.builder()
                .taskId(taskId)
                .fromStatus(TaskStatus.FAILED)
                .toStatus(TaskStatus.RETRYING)
                .message("自动重试第 " + nextRetryCount + "/" + maxRetryCount
                        + " 次，" + RETRY_DELAY_SECONDS + " 秒后重新执行")
                .operatorType("WORKER")
                .createdAt(now)
                .build());
        return nextRetryCount;
    }

    @Override
    @Transactional
    public void markRetryPublishFailed(Long taskId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.markRetryPublishFailed(taskId, reason, now);
        if (updated > 0) {
            logMapper.insert(LogEntity.builder()
                    .taskId(taskId)
                    .fromStatus(TaskStatus.RETRYING)
                    .toStatus(TaskStatus.FAILED)
                    .message(reason)
                    .operatorType("WORKER")
                    .createdAt(now)
                    .build());
        }
    }
}
