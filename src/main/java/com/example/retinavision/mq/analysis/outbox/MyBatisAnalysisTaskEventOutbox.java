package com.example.retinavision.mq.analysis.outbox;

import com.example.retinavision.mapper.analysis.AnalysisOutboxMapper;
import com.example.retinavision.pojo.analysis.entity.AnalysisOutboxEntity;
import com.example.retinavision.pojo.analysis.event.AnalysisTaskRequestedEvent;
import com.example.retinavision.port.analysis.AnalysisTaskEventOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;

public class MyBatisAnalysisTaskEventOutbox implements AnalysisTaskEventOutbox {

    private static final String PENDING = "PENDING";

    private final AnalysisOutboxMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MyBatisAnalysisTaskEventOutbox(
            AnalysisOutboxMapper mapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void append(
            AnalysisTaskRequestedEvent event,
            String eventType,
            int eventVersion) {
        validate(event, eventType, eventVersion);
        LocalDateTime now = LocalDateTime.now(clock);
        AnalysisOutboxEntity entity = AnalysisOutboxEntity.builder()
                .eventKey(event.taskId() + ":" + eventType + ":" + eventVersion)
                .aggregateId(event.taskId())
                .eventType(eventType)
                .eventVersion(eventVersion)
                .payloadJson(serialize(event))
                .status(PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .publishedAt(null)
                .lastError(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        mapper.insert(entity);
    }

    private void validate(
            AnalysisTaskRequestedEvent event,
            String eventType,
            int eventVersion) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (!StringUtils.hasText(eventType)) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (eventVersion <= 0) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
    }

    private String serialize(AnalysisTaskRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize analysis task event", exception);
        }
    }
}
