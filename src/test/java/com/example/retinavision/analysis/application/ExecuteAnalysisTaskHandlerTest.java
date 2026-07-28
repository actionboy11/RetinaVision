package com.example.retinavision.analysis.application;

import com.example.retinavision.analysis.application.command.ExecuteAnalysisTaskCommand;
import com.example.retinavision.analysis.application.model.ExecutionDisposition;
import com.example.retinavision.analysis.application.model.InferenceOutput;
import com.example.retinavision.analysis.application.model.SourceImage;
import com.example.retinavision.analysis.application.port.out.AiInferencePort;
import com.example.retinavision.analysis.application.port.out.AnalysisResultStore;
import com.example.retinavision.analysis.application.port.out.AnalysisTaskRepository;
import com.example.retinavision.analysis.application.port.out.ArtifactStore;
import com.example.retinavision.analysis.application.port.out.ImageQualityProjectionPort;
import com.example.retinavision.analysis.application.port.out.ReportDraftPort;
import com.example.retinavision.analysis.application.port.out.SourceImageReader;
import com.example.retinavision.analysis.application.port.out.TaskAuditLog;
import com.example.retinavision.analysis.domain.model.AnalysisTask;
import com.example.retinavision.analysis.domain.model.AnalysisTaskStatus;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.analysis.domain.model.TaskTransition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecuteAnalysisTaskHandlerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T04:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 4, 0);

    @Test
    void missingTaskIsRequeued() {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.empty();

        ExecutionDisposition result = fixture.handler().execute(command());

        assertThat(result).isEqualTo(ExecutionDisposition.REQUEUE);
        assertThat(fixture.taskRepository.claimCalls).isZero();
        assertThat(fixture.imageReader.calls).isZero();
        assertThat(fixture.aiInferencePort.totalCalls()).isZero();
    }

    @Test
    void persistedFailedTaskIsRequeued() {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(taskWithStatus(AnalysisTaskStatus.FAILED));

        ExecutionDisposition result = fixture.handler().execute(command());

        assertThat(result).isEqualTo(ExecutionDisposition.REQUEUE);
        assertThat(fixture.taskRepository.claimCalls).isZero();
        assertThat(fixture.imageReader.calls).isZero();
    }

    @ParameterizedTest
    @EnumSource(
            value = AnalysisTaskStatus.class,
            names = {"SUCCESS", "RUNNING", "CREATED", "CANCELED"})
    void nonExecutableTaskIsIgnored(AnalysisTaskStatus status) {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(taskWithStatus(status));

        ExecutionDisposition result = fixture.handler().execute(command());

        assertThat(result).isEqualTo(ExecutionDisposition.IGNORED);
        assertThat(fixture.taskRepository.claimCalls).isZero();
        assertThat(fixture.imageReader.calls).isZero();
        assertThat(fixture.auditLog.transitions).isEmpty();
    }

    @Test
    void losingAtomicClaimIsIgnoredBeforeImageOrAiAccess() {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(waitingTask(AnalysisTaskType.VESSEL_SEGMENTATION));
        fixture.taskRepository.claimSucceeds = false;

        ExecutionDisposition result = fixture.handler().execute(command());

        assertThat(result).isEqualTo(ExecutionDisposition.IGNORED);
        assertThat(fixture.taskRepository.claimCalls).isOne();
        assertThat(fixture.taskRepository.claimedAt).isEqualTo(NOW);
        assertThat(fixture.imageReader.calls).isZero();
        assertThat(fixture.aiInferencePort.totalCalls()).isZero();
        assertThat(fixture.auditLog.transitions).isEmpty();
    }

    @Test
    void vesselSegmentationPersistsMaskResultDraftAndSuccessTransitions() {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(waitingTask(AnalysisTaskType.VESSEL_SEGMENTATION));
        fixture.aiInferencePort.segmentOutput = new InferenceOutput(
                Map.of("vesselAreaRatio", 0.21),
                "FSCNet",
                "1.0",
                125,
                "/v1/artifacts/mask-100");
        fixture.resultStore.resultId = 900L;

        ExecutionDisposition result = fixture.handler().execute(
                new ExecuteAnalysisTaskCommand(100L, "trace-100"));

        assertThat(result).isEqualTo(ExecutionDisposition.SUCCESS);
        assertThat(fixture.taskRepository.saved().status())
                .isEqualTo(AnalysisTaskStatus.SUCCESS);
        assertThat(fixture.auditLog.transitions())
                .extracting(TaskTransition::to)
                .containsExactly(AnalysisTaskStatus.RUNNING, AnalysisTaskStatus.SUCCESS);
        assertThat(fixture.imageReader.requestedImageIds).containsExactly(20L);
        assertThat(fixture.aiInferencePort.segmentCalls).isOne();
        assertThat(fixture.aiInferencePort.checkQualityCalls).isZero();
        assertThat(fixture.aiInferencePort.segmentTraceId).isEqualTo("trace-100");
        assertThat(fixture.aiInferencePort.downloadedUrls)
                .containsExactly("/v1/artifacts/mask-100");
        assertThat(fixture.aiInferencePort.downloadTraceId).isEqualTo("trace-100");
        assertThat(fixture.artifactStore.taskId).isEqualTo(100L);
        assertThat(fixture.artifactStore.storedBytes).containsExactly(1, 2, 3);
        assertThat(fixture.resultStore.segmentationCalls).isOne();
        assertThat(fixture.resultStore.segmentationTaskId).isEqualTo(100L);
        assertThat(fixture.resultStore.maskObjectKey).isEqualTo("tasks/100/mask.png");
        assertThat(fixture.reportDraftPort.calls).isOne();
        assertThat(fixture.reportDraftPort.resultId).isEqualTo(900L);
        assertThat(fixture.reportDraftPort.taskId).isEqualTo(100L);
    }

    @Test
    void blankSegmentationMaskUrlFailsClaimedTask() {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(waitingTask(AnalysisTaskType.VESSEL_SEGMENTATION));
        fixture.aiInferencePort.segmentOutput = new InferenceOutput(
                Map.of(), "FSCNet", "1.0", 125, " ");

        ExecutionDisposition result = fixture.handler().execute(command());

        assertThat(result).isEqualTo(ExecutionDisposition.FAILED);
        assertThat(fixture.aiInferencePort.downloadedUrls).isEmpty();
        assertThat(fixture.artifactStore.calls).isZero();
        assertThat(fixture.resultStore.segmentationCalls).isZero();
        assertThat(fixture.taskRepository.saved().status()).isEqualTo(AnalysisTaskStatus.FAILED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "unable to read C:\\retina\\patients\\patient-123\\fundus.png",
            "unable to read /srv/retina/patients/patient-456/fundus.png",
            "token=secret-token-123",
            "password=patient-password",
            "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.sensitive",
            "https://db-user:db-password@example.test/artifacts/patient-789/mask.png"
    })
    void aiFailureNeverPersistsOrAuditsRawSensitiveMessage(String rawMessage) {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(waitingTask(AnalysisTaskType.VESSEL_SEGMENTATION));
        fixture.aiInferencePort.failure = new IllegalStateException(rawMessage);

        ExecutionDisposition result = fixture.handler().execute(command());

        assertThat(result).isEqualTo(ExecutionDisposition.FAILED);
        assertThat(fixture.taskRepository.saved().status()).isEqualTo(AnalysisTaskStatus.FAILED);
        assertThat(fixture.taskRepository.saved().errorMessage())
                .isEqualTo("AI 任务执行失败，请稍后重试或联系管理员")
                .doesNotContain(rawMessage);
        assertThat(fixture.auditLog.transitions())
                .extracting(TaskTransition::to)
                .containsExactly(AnalysisTaskStatus.RUNNING, AnalysisTaskStatus.FAILED);
        assertThat(fixture.auditLog.transitions().get(1).message())
                .isEqualTo("AI 任务执行失败，请稍后重试或联系管理员")
                .doesNotContain(rawMessage);
    }

    @Test
    void qualityDetectionPersistsProjectionWithoutMaskOrDraft() {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(waitingTask(AnalysisTaskType.IMAGE_QUALITY_CHECK));
        fixture.aiInferencePort.qualityOutput = new InferenceOutput(
                Map.of("qualityStatus", "PASS", "qualityScore", 88.5),
                "opencv-quality",
                "rules-v1",
                18,
                null);

        ExecutionDisposition result = fixture.handler().execute(command());

        assertThat(result).isEqualTo(ExecutionDisposition.SUCCESS);
        assertThat(fixture.aiInferencePort.checkQualityCalls).isOne();
        assertThat(fixture.aiInferencePort.segmentCalls).isZero();
        assertThat(fixture.aiInferencePort.downloadedUrls).isEmpty();
        assertThat(fixture.resultStore.qualityCalls).isOne();
        assertThat(fixture.resultStore.qualityTaskId).isEqualTo(100L);
        assertThat(fixture.resultStore.qualityImageId).isEqualTo(20L);
        assertThat(fixture.artifactStore.calls).isZero();
        assertThat(fixture.reportDraftPort.calls).isZero();
        assertThat(fixture.taskRepository.saved().status()).isEqualTo(AnalysisTaskStatus.SUCCESS);
        assertThat(fixture.auditLog.transitions())
                .extracting(TaskTransition::to)
                .containsExactly(AnalysisTaskStatus.RUNNING, AnalysisTaskStatus.SUCCESS);
    }

    @Test
    void qualityExecutionFailureMarksCurrentImageErrorExactlyOnceAndFailsTask() {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(
                waitingTask(AnalysisTaskType.IMAGE_QUALITY_CHECK));
        fixture.aiInferencePort.failure =
                new IllegalStateException("quality inference unavailable");

        ExecutionDisposition result =
                fixture.handler().execute(command());

        assertThat(result).isEqualTo(ExecutionDisposition.FAILED);
        assertThat(fixture.imageQualityProjection.calls).isOne();
        assertThat(fixture.imageQualityProjection.imageFileId).isEqualTo(20L);
        assertThat(fixture.imageQualityProjection.taskId).isEqualTo(100L);
        assertThat(fixture.taskRepository.saved().status())
                .isEqualTo(AnalysisTaskStatus.FAILED);
        assertThat(fixture.auditLog.transitions())
                .extracting(TaskTransition::to)
                .containsExactly(AnalysisTaskStatus.RUNNING, AnalysisTaskStatus.FAILED);
    }

    @Test
    void segmentationExecutionFailureNeverMarksImageQualityError() {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(
                waitingTask(AnalysisTaskType.VESSEL_SEGMENTATION));
        fixture.aiInferencePort.failure =
                new IllegalStateException("segmentation inference unavailable");

        ExecutionDisposition result =
                fixture.handler().execute(command());

        assertThat(result).isEqualTo(ExecutionDisposition.FAILED);
        assertThat(fixture.imageQualityProjection.calls).isZero();
        assertThat(fixture.taskRepository.saved().status())
                .isEqualTo(AnalysisTaskStatus.FAILED);
    }

    @Test
    void qualityProjectionFailurePropagatesBeforeTaskFailureIsPersisted() {
        Fixture fixture = new Fixture();
        fixture.taskRepository.task = Optional.of(
                waitingTask(AnalysisTaskType.IMAGE_QUALITY_CHECK));
        fixture.aiInferencePort.failure =
                new IllegalStateException("quality inference unavailable");
        fixture.imageQualityProjection.failure =
                new IllegalStateException("quality projection unavailable");

        assertThatThrownBy(() -> fixture.handler().execute(command()))
                .isSameAs(fixture.imageQualityProjection.failure);

        assertThat(fixture.imageQualityProjection.calls).isOne();
        assertThat(fixture.taskRepository.saved).isNull();
        assertThat(fixture.auditLog.transitions())
                .extracting(TaskTransition::to)
                .containsExactly(AnalysisTaskStatus.RUNNING);
    }

    @Test
    void lookupAndAtomicClaimFailuresPropagate() {
        Fixture lookupFixture = new Fixture();
        lookupFixture.taskRepository.lookupFailure = new IllegalStateException("database unavailable");

        assertThatThrownBy(() -> lookupFixture.handler().execute(command()))
                .isSameAs(lookupFixture.taskRepository.lookupFailure);

        Fixture claimFixture = new Fixture();
        claimFixture.taskRepository.task = Optional.of(
                waitingTask(AnalysisTaskType.VESSEL_SEGMENTATION));
        claimFixture.taskRepository.claimFailure = new IllegalStateException("claim unavailable");

        assertThatThrownBy(() -> claimFixture.handler().execute(command()))
                .isSameAs(claimFixture.taskRepository.claimFailure);
        assertThat(claimFixture.taskRepository.saved).isNull();
        assertThat(claimFixture.auditLog.transitions).isEmpty();
    }

    @Test
    void commandRejectsInvalidTaskIdOrTraceId() {
        assertThatThrownBy(() -> new ExecuteAnalysisTaskCommand(0L, "trace-100"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskId 必须为正数");
        assertThatThrownBy(() -> new ExecuteAnalysisTaskCommand(100L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("traceId 不能为空");
        assertThatThrownBy(() -> new ExecuteAnalysisTaskCommand(100L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("traceId 不能为空");
    }

    @Test
    void inferenceOutputNormalizesAndDefensivelyCopiesResultJson() {
        InferenceOutput emptyOutput = new InferenceOutput(
                null, "model", "1", 1, null);
        Map<String, Object> mutableResult = new LinkedHashMap<>();
        mutableResult.put("score", 90);
        InferenceOutput copiedOutput = new InferenceOutput(
                mutableResult, "model", "1", 1, null);

        mutableResult.put("score", 10);

        assertThat(emptyOutput.resultJson()).isEmpty();
        assertThat(copiedOutput.resultJson()).containsEntry("score", 90);
        assertThatThrownBy(() -> copiedOutput.resultJson().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void analysisTaskTypeContainsOnlySupportedExecutionBranches() {
        assertThat(AnalysisTaskType.values()).containsExactly(
                AnalysisTaskType.IMAGE_QUALITY_CHECK,
                AnalysisTaskType.VESSEL_SEGMENTATION);
    }

    private ExecuteAnalysisTaskCommand command() {
        return new ExecuteAnalysisTaskCommand(100L, "trace-100");
    }

    private static AnalysisTask waitingTask(AnalysisTaskType type) {
        return AnalysisTask.waiting(100L, "TASK-100", 20L, type, 0, 3);
    }

    private static AnalysisTask taskWithStatus(AnalysisTaskStatus status) {
        return AnalysisTask.rehydrate(
                100L,
                "TASK-100",
                20L,
                AnalysisTaskType.VESSEL_SEGMENTATION,
                status,
                0,
                3,
                status == AnalysisTaskStatus.FAILED ? "previous failure" : null);
    }

    private static final class Fixture {

        private final InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        private final RecordingSourceImageReader imageReader = new RecordingSourceImageReader();
        private final RecordingAiInferencePort aiInferencePort = new RecordingAiInferencePort();
        private final RecordingArtifactStore artifactStore = new RecordingArtifactStore();
        private final RecordingResultStore resultStore = new RecordingResultStore();
        private final RecordingImageQualityProjection imageQualityProjection =
                new RecordingImageQualityProjection();
        private final RecordingAuditLog auditLog = new RecordingAuditLog();
        private final RecordingReportDraftPort reportDraftPort = new RecordingReportDraftPort();

        private ExecuteAnalysisTaskHandler handler() {
            return new ExecuteAnalysisTaskHandler(
                    taskRepository,
                    imageReader,
                    aiInferencePort,
                    artifactStore,
                    resultStore,
                    imageQualityProjection,
                    auditLog,
                    reportDraftPort,
                    CLOCK);
        }
    }

    private static final class InMemoryTaskRepository implements AnalysisTaskRepository {

        private Optional<AnalysisTask> task = Optional.empty();
        private boolean claimSucceeds = true;
        private int claimCalls;
        private LocalDateTime claimedAt;
        private AnalysisTask saved;
        private RuntimeException lookupFailure;
        private RuntimeException claimFailure;

        @Override
        public Optional<AnalysisTask> findById(long taskId) {
            if (lookupFailure != null) {
                throw lookupFailure;
            }
            return task;
        }

        @Override
        public boolean claim(AnalysisTask task, LocalDateTime startedAt) {
            claimCalls++;
            claimedAt = startedAt;
            if (claimFailure != null) {
                throw claimFailure;
            }
            return claimSucceeds;
        }

        @Override
        public void save(AnalysisTask task) {
            saved = task;
        }

        private AnalysisTask saved() {
            return saved;
        }
    }

    private static final class RecordingSourceImageReader implements SourceImageReader {

        private final List<Long> requestedImageIds = new ArrayList<>();
        private int calls;

        @Override
        public SourceImage getRequired(long imageFileId) {
            calls++;
            requestedImageIds.add(imageFileId);
            return new SourceImage(
                    imageFileId,
                    "fundus.png",
                    "image/png",
                    "images/20/fundus.png");
        }
    }

    private static final class RecordingAiInferencePort implements AiInferencePort {

        private InferenceOutput qualityOutput;
        private InferenceOutput segmentOutput;
        private RuntimeException failure;
        private int checkQualityCalls;
        private int segmentCalls;
        private String segmentTraceId;
        private final List<String> downloadedUrls = new ArrayList<>();
        private String downloadTraceId;

        @Override
        public InferenceOutput checkQuality(SourceImage image, String traceId) {
            checkQualityCalls++;
            throwFailureIfConfigured();
            return qualityOutput;
        }

        @Override
        public InferenceOutput segment(SourceImage image, String traceId) {
            segmentCalls++;
            segmentTraceId = traceId;
            throwFailureIfConfigured();
            return segmentOutput;
        }

        @Override
        public byte[] downloadMask(String artifactUrl, String traceId) {
            downloadedUrls.add(artifactUrl);
            downloadTraceId = traceId;
            throwFailureIfConfigured();
            return new byte[]{1, 2, 3};
        }

        private int totalCalls() {
            return checkQualityCalls + segmentCalls + downloadedUrls.size();
        }

        private void throwFailureIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class RecordingArtifactStore implements ArtifactStore {

        private int calls;
        private long taskId;
        private byte[] storedBytes;

        @Override
        public String storeMask(long taskId, byte[] bytes) {
            calls++;
            this.taskId = taskId;
            this.storedBytes = bytes;
            return "tasks/100/mask.png";
        }
    }

    private static final class RecordingResultStore implements AnalysisResultStore {

        private long resultId = 500L;
        private int qualityCalls;
        private long qualityTaskId;
        private long qualityImageId;
        private int segmentationCalls;
        private long segmentationTaskId;
        private String maskObjectKey;

        @Override
        public long saveQuality(long taskId, long imageFileId, InferenceOutput output) {
            qualityCalls++;
            qualityTaskId = taskId;
            qualityImageId = imageFileId;
            return resultId;
        }

        @Override
        public long saveSegmentation(
                long taskId,
                InferenceOutput output,
                String maskObjectKey) {
            segmentationCalls++;
            segmentationTaskId = taskId;
            this.maskObjectKey = maskObjectKey;
            return resultId;
        }
    }

    private static final class RecordingImageQualityProjection
            implements ImageQualityProjectionPort {

        private int calls;
        private long imageFileId;
        private long taskId;
        private RuntimeException failure;

        @Override
        public void markErrorIfCurrent(long imageFileId, long taskId) {
            calls++;
            this.imageFileId = imageFileId;
            this.taskId = taskId;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class RecordingAuditLog implements TaskAuditLog {

        private final List<TaskTransition> transitions = new ArrayList<>();

        @Override
        public void append(long taskId, TaskTransition transition) {
            transitions.add(transition);
        }

        private List<TaskTransition> transitions() {
            return transitions;
        }
    }

    private static final class RecordingReportDraftPort implements ReportDraftPort {

        private int calls;
        private long resultId;
        private long taskId;

        @Override
        public void ensureDraft(long resultId, long taskId) {
            calls++;
            this.resultId = resultId;
            this.taskId = taskId;
        }
    }
}
