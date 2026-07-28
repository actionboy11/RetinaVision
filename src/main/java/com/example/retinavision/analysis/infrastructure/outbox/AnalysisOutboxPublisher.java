package com.example.retinavision.analysis.infrastructure.outbox;

import com.example.retinavision.analysis.application.model.AnalysisTaskRequestedEvent;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mq.AnalysisTaskMessage;
import com.example.retinavision.mq.AnalysisTaskMessagePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class AnalysisOutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(AnalysisOutboxPublisher.class);
    private static final String SAFE_PUBLISH_ERROR = "分析任务消息发布失败";

    private final AnalysisOutboxMapper mapper;
    private final AnalysisTaskMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
    private final AnalysisOutboxProperties properties;
    private final Clock clock;

    public AnalysisOutboxPublisher(
            AnalysisOutboxMapper mapper,
            AnalysisTaskMessagePublisher messagePublisher,
            ObjectMapper objectMapper,
            AnalysisOutboxProperties properties,
            Clock clock) {
        this.mapper = mapper;
        this.messagePublisher = messagePublisher;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${retina.outbox.fixed-delay:1s}")
    public void publishDueEvents() {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            mapper.recoverStaleProcessing(
                    now.minus(properties.getClaimTimeout()), now);
        } catch (RuntimeException exception) {
            log.warn("Analysis outbox stale-claim recovery failed");
        }

        List<Long> dueIds;
        try {
            dueIds = mapper.selectDuePendingIds(now, properties.getBatchSize());
        } catch (RuntimeException exception) {
            log.warn("Analysis outbox pending-event selection failed");
            return;
        }
        if (dueIds == null || dueIds.isEmpty()) {
            return;
        }

        for (Long id : dueIds) {
            if (id == null) {
                continue;
            }
            try {
                publishClaimed(id);
            } catch (RuntimeException exception) {
                log.warn("Analysis outbox event processing failed for id={}", id);
            }
        }
    }

    private void publishClaimed(long id) {
        LocalDateTime claimedAt = LocalDateTime.now(clock);
        if (mapper.claimPending(id, claimedAt) != 1) {
            return;
        }

        AnalysisOutboxEntity entity = null;
        try {
            entity = mapper.selectClaimed(id);
            if (entity == null) {
                throw new IllegalStateException("Claimed analysis outbox event not found");
            }
            AnalysisTaskRequestedEvent event = objectMapper.readValue(
                    entity.getPayloadJson(), AnalysisTaskRequestedEvent.class);
            messagePublisher.publish(toMessage(event));
            LocalDateTime publishedAt = LocalDateTime.now(clock);
            int affectedRows = mapper.markPublished(id, publishedAt);
            if (affectedRows != 1) {
                warnFinalizationConflict(
                        id, "PROCESSING->PUBLISHED", affectedRows);
            }
        } catch (Exception exception) {
            int nextAttemptCount = entity == null || entity.getAttemptCount() == null
                    ? 1
                    : entity.getAttemptCount() + 1;
            LocalDateTime failedAt = LocalDateTime.now(clock);
            int affectedRows = mapper.reschedule(
                    id,
                    nextAttemptCount,
                    failedAt.plus(properties.getRetryDelay()),
                    SAFE_PUBLISH_ERROR,
                    failedAt);
            if (affectedRows != 1) {
                warnFinalizationConflict(
                        id, "PROCESSING->PENDING", affectedRows);
            }
        }
    }

    private void warnFinalizationConflict(
            long id,
            String expectedTransition,
            int affectedRows) {
        log.warn(
                "Analysis outbox conditional finalization conflict "
                        + "id={} expectedTransition={} affectedRows={}",
                id,
                expectedTransition,
                affectedRows);
    }

    private AnalysisTaskMessage toMessage(AnalysisTaskRequestedEvent event) {
        return AnalysisTaskMessage.builder()
                .taskId(event.taskId())
                .taskNo(event.taskNo())
                .caseId(event.caseId())
                .imageFileId(event.imageFileId())
                .taskType(TaskType.valueOf(event.taskType().name()))
                .priority(event.priority())
                .submittedBy(event.submittedBy())
                .submittedAt(event.submittedAt())
                .build();
    }
}
