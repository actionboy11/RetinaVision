package com.example.retinavision.service.clinical.impl;

import com.example.retinavision.enumeration.analysis.TaskStatus;
import com.example.retinavision.exception.shared.BaseException;
import com.example.retinavision.mapper.analysis.AnalysisResultMapper;
import com.example.retinavision.mapper.analysis.LogMapper;
import com.example.retinavision.mapper.analysis.TaskMapper;
import com.example.retinavision.pojo.analysis.entity.AnalysisResultEntity;
import com.example.retinavision.pojo.analysis.entity.LogEntity;
import com.example.retinavision.pojo.analysis.entity.TaskEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClinicalTaskLogServiceImplTest {

    private final AnalysisResultMapper results = mock(AnalysisResultMapper.class);
    private final TaskMapper tasks = mock(TaskMapper.class);
    private final LogMapper logs = mock(LogMapper.class);
    private final ClinicalTaskLogServiceImpl service = new ClinicalTaskLogServiceImpl(results, tasks, logs);

    @Test
    void appendResultEventUsesCurrentTaskStatusWithoutChangingIt() {
        AnalysisResultEntity result = new AnalysisResultEntity();
        result.setId(1L);
        result.setTaskId(2L);
        when(results.selectById(1L)).thenReturn(result);

        TaskEntity task = new TaskEntity();
        task.setId(2L);
        task.setStatus(TaskStatus.SUCCESS);
        when(tasks.selectById(2L)).thenReturn(task);

        service.appendResultEvent(1L, "AI 草稿已生成，仅供医生审核参考", "USER", 9);

        ArgumentCaptor<LogEntity> captor = ArgumentCaptor.forClass(LogEntity.class);
        verify(logs).insert(captor.capture());
        assertThat(captor.getValue().getTaskId()).isEqualTo(2L);
        assertThat(captor.getValue().getFromStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(captor.getValue().getToStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(captor.getValue().getMessage()).isEqualTo("AI 草稿已生成，仅供医生审核参考");
        assertThat(captor.getValue().getOperatorType()).isEqualTo("USER");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(9);
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void missingResultOrTaskIsRejectedInsteadOfPretendingLogSucceeded() {
        assertThatThrownBy(() -> service.appendResultEvent(404L, "医生已签发正式 PDF 报告", "USER", 9))
                .isInstanceOf(BaseException.class);
        verify(logs, never()).insert(org.mockito.ArgumentMatchers.any(LogEntity.class));

        AnalysisResultEntity result = new AnalysisResultEntity();
        result.setId(1L);
        result.setTaskId(2L);
        when(results.selectById(1L)).thenReturn(result);

        assertThatThrownBy(() -> service.appendResultEvent(1L, "医生已签发正式 PDF 报告", "USER", 9))
                .isInstanceOf(BaseException.class);
        verify(logs, never()).insert(org.mockito.ArgumentMatchers.any(LogEntity.class));
    }
}
