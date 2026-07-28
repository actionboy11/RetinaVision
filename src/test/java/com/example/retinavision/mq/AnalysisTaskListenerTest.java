package com.example.retinavision.mq;

import com.example.retinavision.service.AnalysisTaskExecutionService;
import com.example.retinavision.service.AnalysisTaskExecutionService.ExecutionDisposition;
import com.example.retinavision.service.AnalysisTaskRetryService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisTaskListenerTest {
    @Mock private AnalysisTaskExecutionService executionService;
    @Mock private AnalysisTaskRetryService retryService;
    @Mock private AnalysisTaskMessagePublisher messagePublisher;
    @Mock private Channel channel;

    private AnalysisTaskListener listener;
    private AnalysisTaskMessage taskMessage;
    private Message rawMessage;

    @BeforeEach
    void setUp() {
        listener = new AnalysisTaskListener(executionService, retryService, messagePublisher);
        taskMessage = AnalysisTaskMessage.builder().taskId(100L).build();
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(77L);
        rawMessage = new Message(new byte[0], properties);
    }

    @Test
    void acknowledgesSuccessfulTask() throws Exception {
        when(executionService.process(taskMessage)).thenReturn(ExecutionDisposition.SUCCESS);

        listener.handleAnalysisTask(taskMessage, channel, rawMessage);

        verify(channel).basicAck(77L, false);
    }

    @Test
    void requeuesTransactionVisibilityRace() throws Exception {
        when(executionService.process(taskMessage)).thenReturn(ExecutionDisposition.REQUEUE);

        listener.handleAnalysisTask(taskMessage, channel, rawMessage);

        verify(channel).basicNack(77L, false, true);
    }

    @Test
    void deadLettersRecordedInferenceFailure() throws Exception {
        when(executionService.process(taskMessage)).thenReturn(ExecutionDisposition.FAILED);
        when(retryService.prepareAutomaticRetry(100L)).thenReturn(null);

        listener.handleAnalysisTask(taskMessage, channel, rawMessage);

        verify(channel).basicNack(77L, false, false);
    }

    @Test
    void schedulesFailedTaskForDelayedRetryAndAcknowledgesOriginalMessage() throws Exception {
        when(executionService.process(taskMessage)).thenReturn(ExecutionDisposition.FAILED);
        when(retryService.prepareAutomaticRetry(100L)).thenReturn(1);

        listener.handleAnalysisTask(taskMessage, channel, rawMessage);

        verify(messagePublisher).publishForRetry(taskMessage, 1);
        verify(channel).basicAck(77L, false);
    }

    @Test
    void acknowledgesStaleOrDuplicateTask() throws Exception {
        when(executionService.process(taskMessage)).thenReturn(ExecutionDisposition.IGNORED);

        listener.handleAnalysisTask(taskMessage, channel, rawMessage);

        verify(channel).basicAck(77L, false);
    }

    @Test
    void infrastructureExceptionIsDeadLetteredAndPropagated() throws Exception {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(executionService.process(taskMessage)).thenThrow(failure);

        assertThatThrownBy(() ->
                listener.handleAnalysisTask(taskMessage, channel, rawMessage))
                .isSameAs(failure);
        verify(channel).basicNack(77L, false, false);
    }
}
