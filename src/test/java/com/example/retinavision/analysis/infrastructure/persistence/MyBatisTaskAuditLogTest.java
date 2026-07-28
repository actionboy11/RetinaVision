package com.example.retinavision.analysis.infrastructure.persistence;

import com.example.retinavision.analysis.domain.model.AnalysisTaskStatus;
import com.example.retinavision.analysis.domain.model.TaskTransition;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.pojo.Entity.LogEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MyBatisTaskAuditLogTest {

    @Test
    void mapsTransitionExactlyToWorkerAuditEntity() {
        LogMapper mapper = mock(LogMapper.class);
        MyBatisTaskAuditLog auditLog = new MyBatisTaskAuditLog(mapper);
        LocalDateTime at = LocalDateTime.of(2026, 7, 28, 7, 8, 9);

        auditLog.append(100L, new TaskTransition(
                AnalysisTaskStatus.RETRYING,
                AnalysisTaskStatus.RUNNING,
                "worker claimed retry",
                at));

        ArgumentCaptor<LogEntity> captor = ArgumentCaptor.forClass(LogEntity.class);
        verify(mapper).insert(captor.capture());
        LogEntity log = captor.getValue();
        assertThat(log.getTaskId()).isEqualTo(100L);
        assertThat(log.getFromStatus()).isEqualTo(TaskStatus.RETRYING);
        assertThat(log.getToStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(log.getMessage()).isEqualTo("worker claimed retry");
        assertThat(log.getCreatedAt()).isEqualTo(at);
        assertThat(log.getOperatorType()).isEqualTo("WORKER");
        assertThat(log.getOperatorId()).isNull();
    }
}
