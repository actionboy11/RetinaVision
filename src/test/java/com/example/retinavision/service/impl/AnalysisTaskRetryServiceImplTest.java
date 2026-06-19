package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mq.RabbitMqProperties;
import com.example.retinavision.pojo.Entity.LogEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisTaskRetryServiceImplTest {

    @Mock private TaskMapper taskMapper;
    @Mock private LogMapper logMapper;

    private RabbitMqProperties retryProperties() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setAnalysisMaxAutoRetries(3);
        return properties;
    }

    @Test
    void preparesNextAutomaticRetryAtomically() {
        TaskEntity task = failedTask(0, 3);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(taskMapper.prepareAutomaticRetry(any(), any(), any())).thenReturn(1);
        AnalysisTaskRetryServiceImpl service = new AnalysisTaskRetryServiceImpl(taskMapper, logMapper, retryProperties());

        Integer retryCount = service.prepareAutomaticRetry(100L);

        assertThat(retryCount).isEqualTo(1);
        ArgumentCaptor<LogEntity> logCaptor = ArgumentCaptor.forClass(LogEntity.class);
        verify(logMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getFromStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo(TaskStatus.RETRYING);
        assertThat(logCaptor.getValue().getMessage()).contains("1/3").contains("10 秒");
    }

    @Test
    void doesNotRetryAfterConfiguredLimit() {
        TaskEntity task = failedTask(3, 3);
        when(taskMapper.selectById(100L)).thenReturn(task);
        AnalysisTaskRetryServiceImpl service = new AnalysisTaskRetryServiceImpl(taskMapper, logMapper, retryProperties());

        assertThat(service.prepareAutomaticRetry(100L)).isNull();

        verify(taskMapper, never()).prepareAutomaticRetry(any(), any(), any());
        verify(logMapper, never()).insert(any(LogEntity.class));
    }

    @Test
    void losesConcurrentRetryClaimWithoutWritingDuplicateLog() {
        TaskEntity task = failedTask(0, 3);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(taskMapper.prepareAutomaticRetry(any(), any(), any())).thenReturn(0);
        AnalysisTaskRetryServiceImpl service = new AnalysisTaskRetryServiceImpl(taskMapper, logMapper, retryProperties());

        assertThat(service.prepareAutomaticRetry(100L)).isNull();

        verify(logMapper, never()).insert(any(LogEntity.class));
    }

    private TaskEntity failedTask(int retryCount, int maxRetryCount) {
        return TaskEntity.builder()
                .id(100L)
                .status(TaskStatus.FAILED)
                .retryCount(retryCount)
                .maxRetryCount(maxRetryCount)
                .build();
    }
}
