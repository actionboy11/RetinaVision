package com.example.retinavision.analysis.infrastructure.outbox;

import com.example.retinavision.analysis.application.model.AnalysisTaskRequestedEvent;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MyBatisAnalysisTaskEventOutboxTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 16, 30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T16:30:00Z"), ZoneOffset.UTC);

    private AnalysisOutboxMapper mapper;
    private ObjectMapper objectMapper;
    private MyBatisAnalysisTaskEventOutbox outbox;

    @BeforeEach
    void setUp() {
        mapper = mock(AnalysisOutboxMapper.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        outbox = new MyBatisAnalysisTaskEventOutbox(mapper, objectMapper, CLOCK);
    }

    @Test
    void appendStoresExactEventKeyImmutablePayloadAndPendingDefaults() throws Exception {
        AnalysisTaskRequestedEvent event = event();

        outbox.append(event, "ANALYSIS_TASK_REQUESTED", 1);

        ArgumentCaptor<AnalysisOutboxEntity> captor =
                ArgumentCaptor.forClass(AnalysisOutboxEntity.class);
        verify(mapper).insert(captor.capture());
        AnalysisOutboxEntity stored = captor.getValue();
        assertThat(stored.getEventKey()).isEqualTo("42:ANALYSIS_TASK_REQUESTED:1");
        assertThat(stored.getAggregateId()).isEqualTo(42L);
        assertThat(stored.getEventType()).isEqualTo("ANALYSIS_TASK_REQUESTED");
        assertThat(stored.getEventVersion()).isEqualTo(1);
        assertThat(objectMapper.readValue(
                stored.getPayloadJson(), AnalysisTaskRequestedEvent.class)).isEqualTo(event);
        assertThat(stored.getStatus()).isEqualTo("PENDING");
        assertThat(stored.getAttemptCount()).isZero();
        assertThat(stored.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(stored.getPublishedAt()).isNull();
        assertThat(stored.getLastError()).isNull();
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(stored.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @MethodSource("invalidAppendArguments")
    void appendRejectsInvalidArguments(
            AnalysisTaskRequestedEvent event,
            String eventType,
            int eventVersion) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> outbox.append(event, eventType, eventVersion));
    }

    @Test
    void appendPropagatesMapperFailure() {
        IllegalStateException failure = new IllegalStateException("duplicate event key");
        doThrow(failure).when(mapper).insert(any(AnalysisOutboxEntity.class));

        assertThatThrownBy(() -> outbox.append(event(), "ANALYSIS_TASK_REQUESTED", 1))
                .isSameAs(failure);
    }

    private static Stream<Arguments> invalidAppendArguments() {
        return Stream.of(
                Arguments.of(null, "ANALYSIS_TASK_REQUESTED", 1),
                Arguments.of(event(), null, 1),
                Arguments.of(event(), "", 1),
                Arguments.of(event(), " \t", 1),
                Arguments.of(event(), "ANALYSIS_TASK_REQUESTED", 0),
                Arguments.of(event(), "ANALYSIS_TASK_REQUESTED", -1)
        );
    }

    private static AnalysisTaskRequestedEvent event() {
        return new AnalysisTaskRequestedEvent(
                42L,
                "TASK-42",
                7L,
                9L,
                AnalysisTaskType.VESSEL_SEGMENTATION,
                5,
                11,
                LocalDateTime.of(2026, 7, 28, 16, 29));
    }
}
