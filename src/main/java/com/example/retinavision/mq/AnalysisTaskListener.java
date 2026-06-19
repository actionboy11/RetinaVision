package com.example.retinavision.mq;

import com.example.retinavision.service.AnalysisTaskExecutionService;
import com.example.retinavision.service.AnalysisTaskRetryService;
import com.example.retinavision.service.AnalysisTaskExecutionService.ExecutionDisposition;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AnalysisTaskListener {
    private final AnalysisTaskExecutionService executionService;
    private final AnalysisTaskRetryService retryService;
    private final AnalysisTaskMessagePublisher messagePublisher;

    public AnalysisTaskListener(AnalysisTaskExecutionService executionService,
                                AnalysisTaskRetryService retryService,
                                AnalysisTaskMessagePublisher messagePublisher) {
        this.executionService = executionService;
        this.retryService = retryService;
        this.messagePublisher = messagePublisher;
    }

    @RabbitListener(queues = "${retina.mq.analysis-queue}")
    public void handleAnalysisTask(
            AnalysisTaskMessage message,
            Channel channel,
            Message rawMessage) throws Exception {
        // 获取消息的 deliveryTag，用于确认消息处理结果后删除消息
        //rawMessage 是 RabbitMQ 提供的消息对象，包含了消息的元数据和内容
        //deliveryTag 是消息的唯一标识符，用于确认消息处理结果后删除消息的 deliveryTag
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        try {
            // Codex: 调用执行服务处理消息，并根据处理结果进行消息确认或拒绝
            //ExecutionDisposition 是一个枚举类型，表示消息处理的结果状态，包括成功、失败、忽略和重新队列
            ExecutionDisposition disposition = executionService.process(message);
            //basicNack(deliveryTag, boolean multiple, boolean requeue) 方法用于拒绝消息。
            //multiple 表示是否拒绝多个消息，requeue 表示是否将消息重新放回队列
            switch (disposition) {
                case SUCCESS, IGNORED -> channel.basicAck(deliveryTag, false); //basicAck() 方法用于确认消息已被成功处理，RabbitMQ 将从队列中删除该消息
                case REQUEUE -> channel.basicNack(deliveryTag, false, true); //basicNack(deliveryTag, false, true) 方法用于拒绝消息，并将其重新放回队列，等待下次处理
                case FAILED -> handleFailedTask(message, channel, deliveryTag);
            }
        } catch (Exception exception) {
            channel.basicNack(deliveryTag, false, false);
            throw exception;
        }
    }

    private void handleFailedTask(AnalysisTaskMessage message, Channel channel, long deliveryTag) throws Exception {
        // 有限重试与死信恢复功能完善：最多重试任务配置的次数；每次先投递到 10 秒延迟队列，耗尽后才进入死信队列。
        //这个失败任务还有没有重试机会，没有重试机会则拒绝消息
        Integer retryCount = retryService.prepareAutomaticRetry(message.getTaskId());
        if (retryCount == null) {
            //basicNack(deliveryTag, false, false) 方法用于拒绝消息，并且不将其重新放回队列，
            // 这样消息会进入死信队列，方便后续排查失败消息
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            // Codex: 调用消息发布器把消息投递到重试交换机，等待下一次重试处理
            messagePublisher.publishForRetry(message, retryCount);
            //basicAck() 方法用于确认消息已被成功处理，RabbitMQ 将从队列中删除该消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            retryService.markRetryPublishFailed(message.getTaskId(), "自动重试消息投递失败");
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
