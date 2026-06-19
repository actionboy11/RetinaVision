package com.example.retinavision.config;

import com.example.retinavision.mq.RabbitMqProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqRetryConfigTest {

    @Test
    void retryQueueWaitsTenSecondsThenRoutesBackToMainExchange() {
        RabbitMqProperties properties = properties();
        RabbitMqConfig config = new RabbitMqConfig();

        Queue retryQueue = config.analysisRetryQueue(properties);
        DirectExchange retryExchange = config.analysisRetryExchange(properties);
        Binding binding = config.analysisRetryBinding(retryQueue, retryExchange, properties);

        assertThat(retryQueue.getName()).isEqualTo("retina.analysis.task.retry.queue");
        assertThat(retryQueue.getArguments())
                .containsEntry("x-message-ttl", 10_000L)
                .containsEntry("x-dead-letter-exchange", "retina.analysis.exchange")
                .containsEntry("x-dead-letter-routing-key", "retina.analysis.task.created");
        assertThat(binding.getExchange()).isEqualTo("retina.analysis.retry.exchange");
        assertThat(binding.getRoutingKey()).isEqualTo("retina.analysis.task.retry");
    }

    private RabbitMqProperties properties() {
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setAnalysisExchange("retina.analysis.exchange");
        properties.setAnalysisRoutingKey("retina.analysis.task.created");
        properties.setAnalysisRetryExchange("retina.analysis.retry.exchange");
        properties.setAnalysisRetryRoutingKey("retina.analysis.task.retry");
        properties.setAnalysisRetryQueue("retina.analysis.task.retry.queue");
        properties.setAnalysisRetryDelayMs(10_000L);
        return properties;
    }
}
