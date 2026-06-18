package com.example.retinavision.mq;

import com.example.retinavision.service.AnalysisTaskExecutionService;
import com.example.retinavision.service.AnalysisTaskExecutionService.ExecutionDisposition;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AnalysisTaskListener {
    private final AnalysisTaskExecutionService executionService;

    public AnalysisTaskListener(AnalysisTaskExecutionService executionService) {
        this.executionService = executionService;
    }

    @RabbitListener(queues = "${retina.mq.analysis-queue}")
    public void handleAnalysisTask(
            AnalysisTaskMessage message,
            Channel channel,
            Message rawMessage) throws Exception {
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        try {
            ExecutionDisposition disposition = executionService.process(message);
            switch (disposition) {
                case SUCCESS, IGNORED -> channel.basicAck(deliveryTag, false);
                case REQUEUE -> channel.basicNack(deliveryTag, false, true);
                case FAILED -> channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception exception) {
            channel.basicNack(deliveryTag, false, false);
            throw exception;
        }
    }
}
