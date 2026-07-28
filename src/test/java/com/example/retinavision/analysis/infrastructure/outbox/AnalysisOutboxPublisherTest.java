package com.example.retinavision.analysis.infrastructure.outbox;

import com.example.retinavision.analysis.application.model.AnalysisTaskRequestedEvent;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mq.AnalysisTaskMessage;
import com.example.retinavision.mq.AnalysisTaskMessagePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AnalysisOutboxPublisherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 16, 30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T16:30:00Z"), ZoneOffset.UTC);
    private static final String SAFE_ERROR = "分析任务消息发布失败";

    private AnalysisOutboxMapper mapper;
    private AnalysisTaskMessagePublisher messagePublisher;
    private ObjectMapper objectMapper;
    private AnalysisOutboxProperties properties;
    private AnalysisOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        mapper = mock(AnalysisOutboxMapper.class);
        messagePublisher = mock(AnalysisTaskMessagePublisher.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        properties = new AnalysisOutboxProperties();
        properties.setFixedDelay(Duration.ofSeconds(1));
        properties.setBatchSize(50);
        properties.setRetryDelay(Duration.ofSeconds(10));
        properties.setClaimTimeout(Duration.ofMinutes(2));
        publisher = new AnalysisOutboxPublisher(
                mapper, messagePublisher, objectMapper, properties, CLOCK);
        when(mapper.markPublished(anyLong(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(mapper.reschedule(
                anyLong(),
                anyInt(),
                any(LocalDateTime.class),
                anyString(),
                any(LocalDateTime.class)))
                .thenReturn(1);
    }

    @Test
    void confirmedPublicationMarksClaimedEventPublishedAfterBrokerConfirm() throws Exception {
        AnalysisOutboxEntity entity = entity(1L, 2, payload());
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of(1L));
        when(mapper.claimPending(1L, NOW)).thenReturn(1);
        when(mapper.selectClaimed(1L)).thenReturn(entity);

        publisher.publishDueEvents();

        ArgumentCaptor<AnalysisTaskMessage> messageCaptor =
                ArgumentCaptor.forClass(AnalysisTaskMessage.class);
        InOrder order = inOrder(messagePublisher, mapper);
        order.verify(messagePublisher).publish(messageCaptor.capture());
        order.verify(mapper).markPublished(1L, NOW);
        AnalysisTaskMessage message = messageCaptor.getValue();
        assertThat(message.getTaskId()).isEqualTo(42L);
        assertThat(message.getTaskNo()).isEqualTo("TASK-42");
        assertThat(message.getCaseId()).isEqualTo(7L);
        assertThat(message.getImageFileId()).isEqualTo(9L);
        assertThat(message.getTaskType()).isEqualTo(TaskType.VESSEL_SEGMENTATION);
        assertThat(message.getPriority()).isEqualTo(5);
        assertThat(message.getSubmittedBy()).isEqualTo(11);
        assertThat(message.getSubmittedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 28, 16, 29));
    }

    @Test
    void publishFailureReschedulesWithIncrementedAttemptDelayAndFixedSafeError() throws Exception {
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of(1L));
        when(mapper.claimPending(1L, NOW)).thenReturn(1);
        when(mapper.selectClaimed(1L)).thenReturn(entity(1L, 2, payload()));
        doThrow(new IllegalStateException("host=/secret/path credential=abc"))
                .when(messagePublisher).publish(any(AnalysisTaskMessage.class));

        publisher.publishDueEvents();

        verify(mapper).reschedule(
                1L, 3, NOW.plusSeconds(10), SAFE_ERROR, NOW);
        verify(mapper, never()).markPublished(any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void alreadyPublishedRowsAreNotSelectedOrRepublished() {
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of());

        publisher.publishDueEvents();

        verify(messagePublisher, never()).publish(any());
        verify(mapper, never()).claimPending(any(Long.class), any(LocalDateTime.class));
        verify(mapper, never()).markPublished(any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void competingPollersPublishOnlyAfterOneSuccessfulConditionalClaim() throws Exception {
        AtomicBoolean available = new AtomicBoolean(true);
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of(1L));
        when(mapper.claimPending(1L, NOW))
                .thenAnswer(invocation -> available.compareAndSet(true, false) ? 1 : 0);
        when(mapper.selectClaimed(1L)).thenReturn(entity(1L, 0, payload()));
        AnalysisOutboxPublisher competingPublisher = new AnalysisOutboxPublisher(
                mapper, messagePublisher, objectMapper, properties, CLOCK);

        publisher.publishDueEvents();
        competingPublisher.publishDueEvents();

        verify(mapper, times(2)).claimPending(1L, NOW);
        verify(messagePublisher, times(1)).publish(any(AnalysisTaskMessage.class));
        verify(mapper, times(1)).markPublished(1L, NOW);
    }

    @Test
    void staleProcessingRecoveryRunsBeforePendingSelection() {
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of());

        publisher.publishDueEvents();

        InOrder order = inOrder(mapper);
        order.verify(mapper).recoverStaleProcessing(NOW.minusMinutes(2), NOW);
        order.verify(mapper).selectDuePendingIds(NOW, 50);
    }

    @Test
    void failedClaimedRowDoesNotStopNextClaimedRow() throws Exception {
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of(1L, 2L));
        when(mapper.claimPending(1L, NOW)).thenReturn(1);
        when(mapper.claimPending(2L, NOW)).thenReturn(1);
        when(mapper.selectClaimed(1L)).thenReturn(entity(1L, 0, payload()));
        when(mapper.selectClaimed(2L)).thenReturn(entity(2L, 4, payload()));
        doThrow(new IllegalStateException("broker down"))
                .doNothing()
                .when(messagePublisher).publish(any(AnalysisTaskMessage.class));

        publisher.publishDueEvents();

        verify(mapper).reschedule(
                1L, 1, NOW.plusSeconds(10), SAFE_ERROR, NOW);
        verify(mapper).markPublished(2L, NOW);
        verify(messagePublisher, times(2)).publish(any(AnalysisTaskMessage.class));
    }

    @Test
    void advancingClockKeepsEachClaimAndFinalizationTimestampFresh() throws Exception {
        Clock advancingClock = new SequenceClock(
                Instant.parse("2026-07-28T16:30:00Z"),
                Instant.parse("2026-07-28T16:30:01Z"),
                Instant.parse("2026-07-28T16:30:02Z"),
                Instant.parse("2026-07-28T16:30:03Z"),
                Instant.parse("2026-07-28T16:30:04Z"));
        publisher = new AnalysisOutboxPublisher(
                mapper, messagePublisher, objectMapper, properties, advancingClock);
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of(1L, 2L));
        when(mapper.claimPending(1L, NOW.plusSeconds(1))).thenReturn(1);
        when(mapper.claimPending(2L, NOW.plusSeconds(3))).thenReturn(1);
        when(mapper.selectClaimed(1L)).thenReturn(entity(1L, 0, payload()));
        when(mapper.selectClaimed(2L)).thenReturn(entity(2L, 0, payload()));

        publisher.publishDueEvents();

        verify(mapper).recoverStaleProcessing(NOW.minusMinutes(2), NOW);
        verify(mapper).claimPending(1L, NOW.plusSeconds(1));
        verify(mapper).markPublished(1L, NOW.plusSeconds(2));
        verify(mapper).claimPending(2L, NOW.plusSeconds(3));
        verify(mapper).markPublished(2L, NOW.plusSeconds(4));
    }

    @Test
    void malformedPayloadIsRescheduledWithoutLeakingPayloadOrParserError() {
        String sensitivePayload = "{\"patient\":\"secret\",\"path\":\"C:/private/image.png\"";
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of(1L));
        when(mapper.claimPending(1L, NOW)).thenReturn(1);
        when(mapper.selectClaimed(1L)).thenReturn(entity(1L, 6, sensitivePayload));

        publisher.publishDueEvents();

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).reschedule(
                eq(1L),
                eq(7),
                eq(NOW.plusSeconds(10)),
                errorCaptor.capture(),
                eq(NOW));
        assertThat(errorCaptor.getValue())
                .isEqualTo(SAFE_ERROR)
                .doesNotContain("secret", "private", "patient", "Unexpected");
        verify(messagePublisher, never()).publish(any());
        verify(mapper, never()).markPublished(any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void zeroRowMarkPublishedWarnsWithoutUnsafeFallbackOrBatchAbort(
            CapturedOutput output) throws Exception {
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of(1L, 2L));
        when(mapper.claimPending(1L, NOW)).thenReturn(1);
        when(mapper.claimPending(2L, NOW)).thenReturn(1);
        when(mapper.selectClaimed(1L)).thenReturn(entity(1L, 0, payload()));
        when(mapper.selectClaimed(2L)).thenReturn(entity(2L, 0, payload()));
        when(mapper.markPublished(1L, NOW)).thenReturn(0);

        publisher.publishDueEvents();

        verify(messagePublisher, times(2)).publish(any(AnalysisTaskMessage.class));
        verify(mapper).markPublished(1L, NOW);
        verify(mapper).markPublished(2L, NOW);
        verify(mapper, never()).updateById(any(AnalysisOutboxEntity.class));
        verify(mapper, never()).reschedule(
                anyLong(),
                anyInt(),
                any(LocalDateTime.class),
                anyString(),
                any(LocalDateTime.class));
        assertThat(output)
                .contains("id=1", "PROCESSING->PUBLISHED")
                .doesNotContain("TASK-42", "ANALYSIS_TASK_REQUESTED");
    }

    @Test
    void zeroRowRescheduleWarnsWithoutUnsafeFallbackOrBatchAbort(
            CapturedOutput output) throws Exception {
        when(mapper.selectDuePendingIds(NOW, 50)).thenReturn(List.of(1L, 2L));
        when(mapper.claimPending(1L, NOW)).thenReturn(1);
        when(mapper.claimPending(2L, NOW)).thenReturn(1);
        when(mapper.selectClaimed(1L)).thenReturn(entity(1L, 2, payload()));
        when(mapper.selectClaimed(2L)).thenReturn(entity(2L, 0, payload()));
        doThrow(new IllegalStateException("broker credential=secret"))
                .doNothing()
                .when(messagePublisher).publish(any(AnalysisTaskMessage.class));
        when(mapper.reschedule(
                1L, 3, NOW.plusSeconds(10), SAFE_ERROR, NOW))
                .thenReturn(0);

        publisher.publishDueEvents();

        verify(messagePublisher, times(2)).publish(any(AnalysisTaskMessage.class));
        verify(mapper).reschedule(
                1L, 3, NOW.plusSeconds(10), SAFE_ERROR, NOW);
        verify(mapper).markPublished(2L, NOW);
        verify(mapper, never()).updateById(any(AnalysisOutboxEntity.class));
        assertThat(output)
                .contains("id=1", "PROCESSING->PENDING")
                .doesNotContain(
                        "credential=secret",
                        "TASK-42",
                        "ANALYSIS_TASK_REQUESTED");
    }

    private AnalysisOutboxEntity entity(long id, int attemptCount, String payloadJson) {
        return AnalysisOutboxEntity.builder()
                .id(id)
                .eventKey("42:ANALYSIS_TASK_REQUESTED:1")
                .aggregateId(42L)
                .eventType("ANALYSIS_TASK_REQUESTED")
                .eventVersion(1)
                .payloadJson(payloadJson)
                .status("PROCESSING")
                .attemptCount(attemptCount)
                .nextAttemptAt(NOW)
                .createdAt(NOW.minusMinutes(1))
                .updatedAt(NOW)
                .build();
    }

    private String payload() throws Exception {
        return objectMapper.writeValueAsString(new AnalysisTaskRequestedEvent(
                42L,
                "TASK-42",
                7L,
                9L,
                AnalysisTaskType.VESSEL_SEGMENTATION,
                5,
                11,
                LocalDateTime.of(2026, 7, 28, 16, 29)));
    }

    private static final class SequenceClock extends Clock {

        private final Queue<Instant> instants;

        private SequenceClock(Instant... instants) {
            this.instants = new ArrayDeque<>(Arrays.asList(instants));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported");
            }
            return this;
        }

        @Override
        public Instant instant() {
            Instant instant = instants.poll();
            if (instant == null) {
                throw new IllegalStateException("No clock instant remains");
            }
            return instant;
        }
    }
}
