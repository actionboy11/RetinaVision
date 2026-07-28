package com.example.retinavision.analysis.domain.model;

import java.time.LocalDateTime;

public final class AnalysisTask {

    private final long id;
    private final String taskNo;
    private final long imageFileId;
    private final AnalysisTaskType type;
    private AnalysisTaskStatus status;
    private int retryCount;
    private final int maxRetryCount;
    private String errorMessage;

    private AnalysisTask(
            long id,
            String taskNo,
            long imageFileId,
            AnalysisTaskType type,
            AnalysisTaskStatus status,
            int retryCount,
            int maxRetryCount,
            String errorMessage) {
        this.id = id;
        this.taskNo = taskNo;
        this.imageFileId = imageFileId;
        this.type = type;
        this.status = status;
        this.retryCount = retryCount;
        this.maxRetryCount = maxRetryCount;
        this.errorMessage = errorMessage;
    }

    public static AnalysisTask waiting(
            long id,
            String taskNo,
            long imageFileId,
            AnalysisTaskType type,
            int retryCount,
            int maxRetryCount) {
        return new AnalysisTask(
                id, taskNo, imageFileId, type, AnalysisTaskStatus.WAITING,
                retryCount, maxRetryCount, null);
    }

    public static AnalysisTask rehydrate(
            long id,
            String taskNo,
            long imageFileId,
            AnalysisTaskType type,
            AnalysisTaskStatus status,
            int retryCount,
            int maxRetryCount,
            String errorMessage) {
        return new AnalysisTask(
                id, taskNo, imageFileId, type, status,
                retryCount, maxRetryCount, errorMessage);
    }

    public TaskTransition claim(LocalDateTime now) {
        if (status != AnalysisTaskStatus.WAITING && status != AnalysisTaskStatus.RETRYING) {
            throw new IllegalTaskTransitionException(status, AnalysisTaskStatus.RUNNING);
        }
        return transitionTo(AnalysisTaskStatus.RUNNING, "AI Worker 已接收任务，开始处理", now);
    }

    public TaskTransition succeed(LocalDateTime now) {
        requireStatus(AnalysisTaskStatus.RUNNING, AnalysisTaskStatus.SUCCESS);
        errorMessage = null;
        return transitionTo(AnalysisTaskStatus.SUCCESS, "AI Worker 已完成任务", now);
    }

    public TaskTransition fail(String safeMessage, LocalDateTime now) {
        requireStatus(AnalysisTaskStatus.RUNNING, AnalysisTaskStatus.FAILED);
        if (safeMessage == null || safeMessage.isBlank()) {
            throw new IllegalArgumentException("失败信息不能为空");
        }
        String normalizedMessage = safeMessage.replaceAll("[\\r\\n]+", " ");
        String storedMessage = normalizedMessage.length() > 1024
                ? normalizedMessage.substring(0, 1024)
                : normalizedMessage;
        errorMessage = storedMessage;
        return transitionTo(AnalysisTaskStatus.FAILED, storedMessage, now);
    }

    public TaskTransition prepareRetry(LocalDateTime now) {
        requireStatus(AnalysisTaskStatus.FAILED, AnalysisTaskStatus.RETRYING);
        if (retryCount >= maxRetryCount) {
            throw new IllegalTaskTransitionException(status, AnalysisTaskStatus.RETRYING);
        }
        retryCount++;
        errorMessage = null;
        return transitionTo(AnalysisTaskStatus.RETRYING, "任务准备重试", now);
    }

    public TaskTransition cancel(LocalDateTime now) {
        if (status != AnalysisTaskStatus.CREATED && status != AnalysisTaskStatus.WAITING) {
            throw new IllegalTaskTransitionException(status, AnalysisTaskStatus.CANCELED);
        }
        return transitionTo(AnalysisTaskStatus.CANCELED, "任务已取消", now);
    }

    public long id() {
        return id;
    }

    public String taskNo() {
        return taskNo;
    }

    public long imageFileId() {
        return imageFileId;
    }

    public AnalysisTaskType type() {
        return type;
    }

    public AnalysisTaskStatus status() {
        return status;
    }

    public int retryCount() {
        return retryCount;
    }

    public int maxRetryCount() {
        return maxRetryCount;
    }

    public String errorMessage() {
        return errorMessage;
    }

    private void requireStatus(AnalysisTaskStatus expected, AnalysisTaskStatus target) {
        if (status != expected) {
            throw new IllegalTaskTransitionException(status, target);
        }
    }

    private TaskTransition transitionTo(AnalysisTaskStatus target, String message, LocalDateTime now) {
        AnalysisTaskStatus previous = status;
        status = target;
        return new TaskTransition(previous, target, message, now);
    }
}
