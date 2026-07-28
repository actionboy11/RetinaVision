package com.example.retinavision.analysis.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisTaskTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Test
    void waitingTaskCanBeClaimedAndCompleted() {
        AnalysisTask task = waitingTask();

        TaskTransition running = task.claim(now);
        TaskTransition success = task.succeed(now.plusSeconds(5));

        assertThat(running).isEqualTo(new TaskTransition(
                AnalysisTaskStatus.WAITING, AnalysisTaskStatus.RUNNING,
                "AI Worker 已接收任务，开始处理", now));
        assertThat(success.from()).isEqualTo(AnalysisTaskStatus.RUNNING);
        assertThat(success.to()).isEqualTo(AnalysisTaskStatus.SUCCESS);
        assertThat(success.at()).isEqualTo(now.plusSeconds(5));
        assertThat(task.status()).isEqualTo(AnalysisTaskStatus.SUCCESS);
        assertThat(task.errorMessage()).isNull();
    }

    @Test
    void completedTaskCannotBeClaimedAgain() {
        AnalysisTask task = AnalysisTask.rehydrate(
                100L, "TASK-100", 20L,
                AnalysisTaskType.VESSEL_SEGMENTATION,
                AnalysisTaskStatus.SUCCESS, 0, 3, null);

        assertThatThrownBy(() -> task.claim(now))
                .isInstanceOf(IllegalTaskTransitionException.class)
                .hasMessage("任务不能从 SUCCESS 转换为 RUNNING");
    }

    @Test
    void failedTaskCanRetryOnlyBelowLimit() {
        AnalysisTask task = AnalysisTask.rehydrate(
                100L, "TASK-100", 20L,
                AnalysisTaskType.VESSEL_SEGMENTATION,
                AnalysisTaskStatus.FAILED, 2, 3, "previous failure");

        TaskTransition retrying = task.prepareRetry(now);

        assertThat(retrying.from()).isEqualTo(AnalysisTaskStatus.FAILED);
        assertThat(retrying.to()).isEqualTo(AnalysisTaskStatus.RETRYING);
        assertThat(task.status()).isEqualTo(AnalysisTaskStatus.RETRYING);
        assertThat(task.retryCount()).isEqualTo(3);
        assertThat(task.errorMessage()).isNull();
    }

    @Test
    void failedTaskAtRetryLimitCannotBeRetried() {
        AnalysisTask task = AnalysisTask.rehydrate(
                100L, "TASK-100", 20L,
                AnalysisTaskType.VESSEL_SEGMENTATION,
                AnalysisTaskStatus.FAILED, 3, 3, "previous failure");

        assertThatThrownBy(() -> task.prepareRetry(now))
                .isInstanceOf(IllegalTaskTransitionException.class)
                .hasMessage("任务不能从 FAILED 转换为 RETRYING");
    }

    @Test
    void runningTaskCanFailWithSafeMessage() {
        AnalysisTask task = waitingTask();
        task.claim(now);

        TaskTransition failed = task.fail("AI service timeout", now.plusSeconds(5));

        assertThat(failed.from()).isEqualTo(AnalysisTaskStatus.RUNNING);
        assertThat(failed.to()).isEqualTo(AnalysisTaskStatus.FAILED);
        assertThat(failed.message()).isEqualTo("AI service timeout");
        assertThat(task.status()).isEqualTo(AnalysisTaskStatus.FAILED);
        assertThat(task.errorMessage()).isEqualTo("AI service timeout");
    }

    @Test
    void runningTaskRejectsBlankFailureMessage() {
        AnalysisTask task = waitingTask();
        task.claim(now);

        assertThatThrownBy(() -> task.fail("  ", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void waitingTaskCanBeCanceled() {
        AnalysisTask task = waitingTask();

        TaskTransition canceled = task.cancel(now);

        assertThat(canceled.from()).isEqualTo(AnalysisTaskStatus.WAITING);
        assertThat(canceled.to()).isEqualTo(AnalysisTaskStatus.CANCELED);
        assertThat(task.status()).isEqualTo(AnalysisTaskStatus.CANCELED);
    }

    private AnalysisTask waitingTask() {
        return AnalysisTask.waiting(
                100L, "TASK-100", 20L,
                AnalysisTaskType.VESSEL_SEGMENTATION, 0, 3);
    }
}
