package com.example.retinavision.analysis.infrastructure.reporting;

import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.service.AnalysisReportService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyReportDraftGatewayTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final AnalysisReportService reportService = mock(AnalysisReportService.class);
    private final LegacyReportDraftGateway gateway =
            new LegacyReportDraftGateway(taskMapper, reportService);

    @Test
    void rejectsMissingTaskOrMissingSubmitter() {
        when(taskMapper.selectById(100L)).thenReturn(null);
        when(taskMapper.selectById(101L)).thenReturn(
                TaskEntity.builder().id(101L).submittedBy(null).build());

        assertMissingSubmitter(100L);
        assertMissingSubmitter(101L);
    }

    @Test
    void delegatesDraftCreationUsingLegacyTaskSubmitter() {
        when(taskMapper.selectById(100L)).thenReturn(
                TaskEntity.builder().id(100L).submittedBy(42).build());

        gateway.ensureDraft(501L, 100L);

        verify(reportService).getOrCreateDraft(501L, 42);
    }

    private void assertMissingSubmitter(long taskId) {
        assertThatThrownBy(() -> gateway.ensureDraft(501L, taskId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("任务提交人不存在");
    }
}
