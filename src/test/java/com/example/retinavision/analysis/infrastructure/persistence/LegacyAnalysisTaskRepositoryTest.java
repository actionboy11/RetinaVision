package com.example.retinavision.analysis.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.retinavision.analysis.domain.model.AnalysisTask;
import com.example.retinavision.analysis.domain.model.AnalysisTaskStatus;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.TaskEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyAnalysisTaskRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T05:06:07Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 5, 6, 7);

    @Mock
    private TaskMapper taskMapper;

    @Test
    void mapsEveryLegacyTaskTypeAndStatusByExactEnumName() {
        LegacyAnalysisTaskRepository repository =
                new LegacyAnalysisTaskRepository(taskMapper, CLOCK);
        long id = 1L;

        for (TaskType type : TaskType.values()) {
            for (TaskStatus status : TaskStatus.values()) {
                TaskEntity entity = TaskEntity.builder()
                        .id(id)
                        .taskNo("TASK-" + id)
                        .imageFileId(20L)
                        .taskType(type)
                        .status(status)
                        .retryCount(1)
                        .maxRetryCount(3)
                        .errorMessage("previous")
                        .build();
                when(taskMapper.selectById(id)).thenReturn(entity);

                AnalysisTask mapped = repository.findById(id).orElseThrow();

                assertThat(mapped.type().name()).isEqualTo(type.name());
                assertThat(mapped.status().name()).isEqualTo(status.name());
                assertThat(mapped.taskNo()).isEqualTo("TASK-" + id);
                assertThat(mapped.imageFileId()).isEqualTo(20L);
                assertThat(mapped.retryCount()).isOne();
                assertThat(mapped.maxRetryCount()).isEqualTo(3);
                assertThat(mapped.errorMessage()).isEqualTo("previous");
                id++;
            }
        }
    }

    @Test
    void claimUsesAtomicMapperUpdateAndOnlyOneAffectedRowWins() {
        LegacyAnalysisTaskRepository repository =
                new LegacyAnalysisTaskRepository(taskMapper, CLOCK);
        AnalysisTask task = AnalysisTask.waiting(
                100L, "TASK-100", 20L, AnalysisTaskType.VESSEL_SEGMENTATION, 0, 3);
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 28, 5, 0);
        when(taskMapper.claimForExecution(100L, startedAt)).thenReturn(1, 0);

        assertThat(repository.claim(task, startedAt)).isTrue();
        assertThat(repository.claim(task, startedAt)).isFalse();
        verify(taskMapper, org.mockito.Mockito.times(2))
                .claimForExecution(100L, startedAt);
    }

    @ParameterizedTest
    @EnumSource(
            value = AnalysisTaskStatus.class,
            names = {"SUCCESS", "FAILED"})
    void savePersistsOnlyMutableFieldsAndAddsClockTimeForEveryTerminalStatus(
            AnalysisTaskStatus terminalStatus) {
        LegacyAnalysisTaskRepository repository =
                new LegacyAnalysisTaskRepository(taskMapper, CLOCK);
        AnalysisTask task = AnalysisTask.rehydrate(
                100L, "TASK-100", 20L, AnalysisTaskType.VESSEL_SEGMENTATION,
                terminalStatus, 2, 3,
                terminalStatus == AnalysisTaskStatus.FAILED ? "failed safely" : null);

        repository.save(task);

        UpdateWrapper<TaskEntity> update = captureUpdate();
        assertThat(update.getExpression().getSqlSegment()).contains("id");
        assertThat(update.getSqlSet())
                .contains("status")
                .contains("retry_count")
                .contains("error_message")
                .contains("updated_at")
                .contains("finished_at");
        assertThat(update.getParamNameValuePairs().values())
                .contains(100L, TaskStatus.valueOf(terminalStatus.name()), 2, NOW);
        if (task.errorMessage() != null) {
            assertThat(update.getParamNameValuePairs().values())
                    .contains(task.errorMessage());
        }
    }

    @Test
    void saveDoesNotInventFinishedTimeForNonterminalStatus() {
        LegacyAnalysisTaskRepository repository =
                new LegacyAnalysisTaskRepository(taskMapper, CLOCK);
        AnalysisTask task = AnalysisTask.rehydrate(
                101L, "TASK-101", 21L, AnalysisTaskType.IMAGE_QUALITY_CHECK,
                AnalysisTaskStatus.RUNNING, 0, 3, null);

        repository.save(task);

        UpdateWrapper<TaskEntity> update = captureUpdate();
        assertThat(update.getExpression().getSqlSegment()).contains("id");
        assertThat(update.getSqlSet())
                .contains("error_message")
                .contains("updated_at")
                .doesNotContain("finished_at");
        assertThat(update.getParamNameValuePairs().values())
                .contains(101L, TaskStatus.RUNNING, 0, NOW);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private UpdateWrapper<TaskEntity> captureUpdate() {
        ArgumentCaptor<UpdateWrapper<TaskEntity>> captor =
                ArgumentCaptor.forClass((Class) UpdateWrapper.class);
        verify(taskMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }
}
