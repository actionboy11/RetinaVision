package com.example.retinavision.service.impl;

import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mq.AnalysisTaskMessage;
import com.example.retinavision.mq.AnalysisTaskMessagePublisher;
import com.example.retinavision.mq.RabbitMqProperties;
import com.example.retinavision.service.AnalysisTaskRetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class DeadLetterRecoveryServiceImplTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private MessageConverter messageConverter;
    @Mock private TaskMapper taskMapper;
    @Mock private LogMapper logMapper;
    @Mock private AnalysisTaskMessagePublisher publisher;
    @Mock private AnalysisTaskRetryService retryService;

    @Test
    void recoversDeadLetterAndPublishesFreshMainQueueMessage() {
        RabbitMqProperties properties = properties();
        Message raw = new Message(new byte[0], new MessageProperties());
        AnalysisTaskMessage taskMessage = AnalysisTaskMessage.builder().taskId(100L).build();
        when(rabbitTemplate.receive("dead.queue")).thenReturn(raw, (Message) null);
        when(rabbitTemplate.getMessageConverter()).thenReturn(messageConverter);
        when(messageConverter.fromMessage(raw)).thenReturn(taskMessage);
        when(taskMapper.prepareDeadLetterRecovery(eq(100L), eq(9), any(LocalDateTime.class))).thenReturn(1);
        DeadLetterRecoveryServiceImpl service = new DeadLetterRecoveryServiceImpl(
                rabbitTemplate, properties, taskMapper, logMapper, publisher, retryService);

        int recovered = service.recover(9, 10);

        assertThat(recovered).isEqualTo(1);
        verify(publisher).publish(taskMessage);
    }

    private RabbitMqProperties properties() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setAnalysisDeadLetterQueue("dead.queue");
        properties.setAnalysisDeadLetterExchange("dead.exchange");
        properties.setAnalysisDeadLetterRoutingKey("dead.key");
        return properties;
    }
}
