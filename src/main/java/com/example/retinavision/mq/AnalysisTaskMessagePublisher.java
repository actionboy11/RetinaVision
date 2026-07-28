package com.example.retinavision.mq;

import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AnalysisTaskMessagePublisher {

    private static final long CONFIRM_TIMEOUT_SECONDS = 5L;

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties properties;

    public AnalysisTaskMessagePublisher(RabbitTemplate rabbitTemplate, RabbitMqProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(AnalysisTaskMessage message) {
        publishConfirmed(properties.getAnalysisExchange(), properties.getAnalysisRoutingKey(), message, null);
    }

    public void publishForRetry(AnalysisTaskMessage message, int retryCount) {
        // 有限重试与死信恢复功能完善：失败任务进入 TTL 重试队列，固定等待 10 秒后再回到主队列。
        publishConfirmed(
                properties.getAnalysisRetryExchange(),
                properties.getAnalysisRetryRoutingKey(),
                message,
                retryCount
        );
    }

    private void publishConfirmed(String exchange,
                                  String routingKey,
                                  AnalysisTaskMessage message,
                                  Integer retryCount) {
        //CorrelationData: 用于关联消息和确认回调，确保消息被正确处理。给这次消息投递一个编号
        CorrelationData correlationData = new CorrelationData(String.valueOf(message.getTaskId()));
        try {
            // Codex: convertAndSend 只代表客户端已发出消息；后面的 confirm 才能证明 RabbitMQ Broker 已确认收到。
            if (retryCount == null) {
                rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);
            } else {
                rabbitTemplate.convertAndSend(
                        exchange,
                        routingKey,
                        message,
                        rabbitMessage -> {
                            rabbitMessage.getMessageProperties().setHeader("x-retry-count", retryCount);
                            return rabbitMessage;
                        },
                        correlationData
                );
            }

            //getFuture() 方法用于获取确认回调，确保消息被正确处理。
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new BaseException(
                        ErrorMessageSignal.MQ_DELIVERY_ERROR,
                        ErrorMessageContant.MQ_DELIVERY_ERROR_MSG
                );
            }
            if (correlationData.getReturned() != null) {
                throw new BaseException(
                        ErrorMessageSignal.MQ_DELIVERY_ERROR,
                        ErrorMessageContant.MQ_DELIVERY_ERROR_MSG
                );
            }
        }
        catch (BaseException exception) {
            throw exception;
            //RabbitMQ 连不上，交换机不存在，网络异常，等待 confirm 时线程被中断
        } catch (AmqpException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BaseException(
                    ErrorMessageSignal.MQ_DELIVERY_ERROR,
                    ErrorMessageContant.MQ_DELIVERY_ERROR_MSG
            );
        } catch (Exception exception) {
            throw new BaseException(
                    ErrorMessageSignal.MQ_DELIVERY_ERROR,
                    ErrorMessageContant.MQ_DELIVERY_ERROR_MSG
            );
        }
    }
}
