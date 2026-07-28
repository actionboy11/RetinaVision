package com.example.retinavision.analysis.application;

import com.example.retinavision.analysis.application.command.ExecuteAnalysisTaskCommand;
import com.example.retinavision.analysis.application.model.ExecutionDisposition;
import com.example.retinavision.analysis.application.model.InferenceOutput;
import com.example.retinavision.analysis.application.model.SourceImage;
import com.example.retinavision.analysis.application.port.out.AiInferencePort;
import com.example.retinavision.analysis.application.port.out.AnalysisResultStore;
import com.example.retinavision.analysis.application.port.out.AnalysisTaskRepository;
import com.example.retinavision.analysis.application.port.out.ArtifactStore;
import com.example.retinavision.analysis.application.port.out.ReportDraftPort;
import com.example.retinavision.analysis.application.port.out.SourceImageReader;
import com.example.retinavision.analysis.application.port.out.TaskAuditLog;
import com.example.retinavision.analysis.domain.model.AnalysisTask;
import com.example.retinavision.analysis.domain.model.AnalysisTaskStatus;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.analysis.domain.model.TaskTransition;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

public final class ExecuteAnalysisTaskHandler implements ExecuteAnalysisTaskUseCase {

    private final AnalysisTaskRepository taskRepository;
    private final SourceImageReader sourceImageReader;
    private final AiInferencePort aiInferencePort;
    private final ArtifactStore artifactStore;
    private final AnalysisResultStore resultStore;
    private final TaskAuditLog auditLog;
    private final ReportDraftPort reportDraftPort;
    private final Clock clock;

    public ExecuteAnalysisTaskHandler(
            AnalysisTaskRepository taskRepository,
            SourceImageReader sourceImageReader,
            AiInferencePort aiInferencePort,
            ArtifactStore artifactStore,
            AnalysisResultStore resultStore,
            TaskAuditLog auditLog,
            ReportDraftPort reportDraftPort,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.sourceImageReader = sourceImageReader;
        this.aiInferencePort = aiInferencePort;
        this.artifactStore = artifactStore;
        this.resultStore = resultStore;
        this.auditLog = auditLog;
        this.reportDraftPort = reportDraftPort;
        this.clock = clock;
    }

    @Override
    public ExecutionDisposition execute(ExecuteAnalysisTaskCommand command) {
        Optional<AnalysisTask> foundTask = taskRepository.findById(command.taskId());
        if (foundTask.isEmpty()) {
            return ExecutionDisposition.REQUEUE;
        }

        AnalysisTask task = foundTask.get();
        if (task.status() == AnalysisTaskStatus.FAILED) {
            return ExecutionDisposition.REQUEUE;
        }
        if (task.status() != AnalysisTaskStatus.WAITING
                && task.status() != AnalysisTaskStatus.RETRYING) {
            return ExecutionDisposition.IGNORED;
        }

        LocalDateTime startedAt = LocalDateTime.now(clock);
        TaskTransition running = task.claim(startedAt);
        if (!taskRepository.claim(task, startedAt)) {
            return ExecutionDisposition.IGNORED;
        }
        auditLog.append(task.id(), running);

        try {
            executeClaimedTask(task, command.traceId());
        } catch (Exception exception) {
            return failTask(task, exception);
        }

        TaskTransition success = task.succeed(LocalDateTime.now(clock));
        taskRepository.save(task);
        auditLog.append(task.id(), success);
        return ExecutionDisposition.SUCCESS;
    }

    private void executeClaimedTask(AnalysisTask task, String traceId) {
        SourceImage image = sourceImageReader.getRequired(task.imageFileId());
        if (task.type() == AnalysisTaskType.IMAGE_QUALITY_CHECK) {
            InferenceOutput output = aiInferencePort.checkQuality(image, traceId);
            resultStore.saveQuality(task.id(), task.imageFileId(), output);
            return;
        }

        InferenceOutput output = aiInferencePort.segment(image, traceId);
        String maskUrl = output.maskUrl();
        if (maskUrl == null || maskUrl.isBlank()) {
            throw new IllegalStateException("AI 响应缺少 maskUrl");
        }
        byte[] maskBytes = aiInferencePort.downloadMask(maskUrl, traceId);
        String maskObjectKey = artifactStore.storeMask(task.id(), maskBytes);
        long resultId = resultStore.saveSegmentation(task.id(), output, maskObjectKey);
        reportDraftPort.ensureDraft(resultId, task.id());
    }

    private ExecutionDisposition failTask(AnalysisTask task, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        String safeMessage = ("AI 任务执行失败：" + message)
                .replaceAll("[\\r\\n]+", " ");
        TaskTransition failure = task.fail(safeMessage, LocalDateTime.now(clock));
        taskRepository.save(task);
        auditLog.append(task.id(), failure);
        return ExecutionDisposition.FAILED;
    }
}
