package com.example.retinavision.analysis.infrastructure.mq;

import com.example.retinavision.analysis.application.command.ExecuteAnalysisTaskCommand;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mq.AnalysisTaskMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisTaskMessageAdapterTest {

    private final AnalysisTaskMessageAdapter adapter = new AnalysisTaskMessageAdapter();

    @Test
    void validMessageUsesOnlyTaskIdAndProvidedTraceId() {
        AnalysisTaskMessage message = AnalysisTaskMessage.builder()
                .taskId(100L)
                .taskNo("untrusted-task-no")
                .imageFileId(999L)
                .taskType(TaskType.IMAGE_QUALITY_CHECK)
                .build();

        Optional<ExecuteAnalysisTaskCommand> result =
                adapter.toCommand(message, "trace-100");

        assertThat(result).contains(
                new ExecuteAnalysisTaskCommand(100L, "trace-100"));
    }

    @Test
    void nullMessageIsRejected() {
        assertThat(adapter.toCommand(null, "trace-100")).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void nullOrNonPositiveTaskIdIsRejected(Long taskId) {
        AnalysisTaskMessage message = AnalysisTaskMessage.builder()
                .taskId(taskId)
                .build();

        assertThat(adapter.toCommand(message, "trace-100")).isEmpty();
    }
}
