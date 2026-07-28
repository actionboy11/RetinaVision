package com.example.retinavision.service.impl;

import com.example.retinavision.analysis.application.ExecuteAnalysisTaskUseCase;
import com.example.retinavision.analysis.application.model.ExecutionDisposition;
import com.example.retinavision.analysis.infrastructure.mq.AnalysisTaskMessageAdapter;
import com.example.retinavision.mq.AnalysisTaskMessage;
import com.example.retinavision.service.AnalysisTaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisTaskExecutionServiceImplTest {

    @Mock
    private ExecuteAnalysisTaskUseCase useCase;

    private AnalysisTaskExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnalysisTaskExecutionServiceImpl(
                useCase,
                new AnalysisTaskMessageAdapter());
    }

    @ParameterizedTest
    @EnumSource(ExecutionDisposition.class)
    void mapsUseCaseDispositionByExactEnumName(ExecutionDisposition disposition) {
        when(useCase.execute(any())).thenReturn(disposition);

        AnalysisTaskExecutionService.ExecutionDisposition result =
                service.process(AnalysisTaskMessage.builder().taskId(100L).build());

        assertThat(result).isEqualTo(
                AnalysisTaskExecutionService.ExecutionDisposition.valueOf(
                        disposition.name()));
        verify(useCase).execute(argThat(command ->
                command.taskId() == 100L
                        && command.traceId().startsWith("task-100-")));
    }

    @Test
    void nullMessageIsIgnoredWithoutCallingUseCase() {
        AnalysisTaskExecutionService.ExecutionDisposition result = service.process(null);

        assertThat(result).isEqualTo(
                AnalysisTaskExecutionService.ExecutionDisposition.IGNORED);
        verify(useCase, never()).execute(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void invalidTaskIdIsIgnoredWithoutCallingUseCase(Long taskId) {
        AnalysisTaskExecutionService.ExecutionDisposition result =
                service.process(AnalysisTaskMessage.builder().taskId(taskId).build());

        assertThat(result).isEqualTo(
                AnalysisTaskExecutionService.ExecutionDisposition.IGNORED);
        verify(useCase, never()).execute(any());
    }

    @Test
    void useCaseInfrastructureExceptionPropagates() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(useCase.execute(any())).thenThrow(failure);

        assertThatThrownBy(() ->
                service.process(AnalysisTaskMessage.builder().taskId(100L).build()))
                .isSameAs(failure);
    }
}
