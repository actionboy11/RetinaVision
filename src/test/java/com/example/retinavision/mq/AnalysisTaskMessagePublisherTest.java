package com.example.retinavision.mq;

import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class AnalysisTaskMessagePublisherTest {

    private static final String EXCHANGE = "retina.analysis.exchange";
    private static final String ROUTING_KEY = "retina.analysis.task.created";

    private RabbitTemplate rabbitTemplate;
    private AnalysisTaskMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setAnalysisExchange(EXCHANGE);
        properties.setAnalysisRoutingKey(ROUTING_KEY);
        publisher = new AnalysisTaskMessagePublisher(rabbitTemplate, properties);
    }

    @Test
    void ackWithoutReturnedMessageSucceeds() {
        AnalysisTaskMessage message = message();
        completePublishWith(true, false, message);

        assertThatCode(() -> publisher.publish(message)).doesNotThrowAnyException();
    }

    @Test
    void ackWithReturnedMessageThrowsMqDeliveryError() {
        AnalysisTaskMessage message = message();
        completePublishWith(true, true, message);

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("code", ErrorMessageSignal.MQ_DELIVERY_ERROR)
                .hasMessage(ErrorMessageContant.MQ_DELIVERY_ERROR_MSG);
    }

    @Test
    void nackThrowsMqDeliveryError() {
        AnalysisTaskMessage message = message();
        completePublishWith(false, false, message);

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("code", ErrorMessageSignal.MQ_DELIVERY_ERROR)
                .hasMessage(ErrorMessageContant.MQ_DELIVERY_ERROR_MSG);
    }

    private void completePublishWith(
            boolean ack,
            boolean returned,
            AnalysisTaskMessage message) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            if (returned) {
                correlationData.setReturned(new ReturnedMessage(
                        new Message("sensitive-body".getBytes()),
                        312,
                        "NO_ROUTE",
                        EXCHANGE,
                        ROUTING_KEY));
            }
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                same(message),
                any(CorrelationData.class));
    }

    private AnalysisTaskMessage message() {
        return AnalysisTaskMessage.builder()
                .taskId(100L)
                .taskNo("TASK-100")
                .caseId(10L)
                .imageFileId(20L)
                .taskType(TaskType.VESSEL_SEGMENTATION)
                .priority(5)
                .submittedBy(7)
                .submittedAt(LocalDateTime.of(2026, 7, 28, 16, 29))
                .build();
    }
}
