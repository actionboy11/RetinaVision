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
        //CorrelationData: 用于关联消息和确认回调，确保消息被正确处理。给这次消息投递一个编号
        CorrelationData correlationData = new CorrelationData(String.valueOf(message.getTaskId()));
        try {
            // Codex: convertAndSend 只代表客户端已发出消息；后面的 confirm 才能证明 RabbitMQ Broker 已确认收到。
            rabbitTemplate.convertAndSend(
                    properties.getAnalysisExchange(),
                    properties.getAnalysisRoutingKey(),
                    message,
                    correlationData
            );

            //getFuture() 方法用于获取确认回调，确保消息被正确处理。
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
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
