package com.example.retinavision.config;

import com.example.retinavision.mq.RabbitMqProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 相关 Bean 配置。
 *
 * 这个类负责把“交换机、队列、绑定关系、消息转换器、生产者模板、消费者容器”交给 Spring 管理。
 * 项目启动时，Spring AMQP 会根据这些 Bean 自动向 RabbitMQ 声明对应的交换机和队列。
 */
@Configuration
public class RabbitMqConfig {

    /**
     * 创建分析任务交换机。
     *
     * DirectExchange 会根据 routing key 精确路由消息。
     * createTask 投递消息时会把消息发到这个交换机，再由交换机转发到任务队列。
     */
    @Bean
    public DirectExchange analysisExchange(RabbitMqProperties properties) {
        return new DirectExchange(properties.getAnalysisExchange(), true, false);
    }

    /**
     * 创建分析任务死信交换机。
     *
     * 当消费者处理消息失败，并且 basicNack(requeue=false) 时，消息不会回到原队列，
     * 而是进入这个死信交换机，方便后续排查失败消息。
     */
    @Bean
    public DirectExchange analysisDeadLetterExchange(RabbitMqProperties properties) {
        return new DirectExchange(properties.getAnalysisDeadLetterExchange(), true, false);
    }

    /**
     * 创建分析任务主队列。
     *
     * Worker 监听这个队列来获取待分析任务。
     * args 中配置了死信交换机和死信 routing key，用来决定消费失败的消息应该转发到哪里。
     */
    @Bean
    public Queue analysisQueue(RabbitMqProperties properties) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", properties.getAnalysisDeadLetterExchange());
        args.put("x-dead-letter-routing-key", properties.getAnalysisDeadLetterRoutingKey());
        return new Queue(properties.getAnalysisQueue(), true, false, false, args);
    }

    /**
     * 创建分析任务死信队列。
     *
     * 消费失败且不重新入队的消息最终会进入这个队列。
     * 后续可以在 RabbitMQ 管理后台查看，也可以单独写补偿程序处理。
     */
    @Bean
    public Queue analysisDeadLetterQueue(RabbitMqProperties properties) {
        return new Queue(properties.getAnalysisDeadLetterQueue(), true);
    }

    /**
     * 绑定分析任务交换机和主队列。
     *
     * 只有 routing key 等于 analysisRoutingKey 的消息，才会从 analysisExchange 路由到 analysisQueue。
     */
    @Bean
    public Binding analysisBinding(Queue analysisQueue,
                                   DirectExchange analysisExchange,
                                   RabbitMqProperties properties) {
        return BindingBuilder.bind(analysisQueue)
                .to(analysisExchange)
                .with(properties.getAnalysisRoutingKey());
    }

    /**
     * 绑定死信交换机和死信队列。
     *
     * 当主队列中的消息消费失败并进入死信交换机后，
     * RabbitMQ 会根据 analysisDeadLetterRoutingKey 把消息路由到死信队列。
     */
    @Bean
    public Binding analysisDeadLetterBinding(Queue analysisDeadLetterQueue,
                                             DirectExchange analysisDeadLetterExchange,
                                             RabbitMqProperties properties) {
        return BindingBuilder.bind(analysisDeadLetterQueue)
                .to(analysisDeadLetterExchange)
                .with(properties.getAnalysisDeadLetterRoutingKey());
    }

    /**
     * 创建 JSON 消息转换器。
     *
     * 生产者发送 AnalysisTaskMessage 时，会把 Java 对象序列化为 JSON；
     * 消费者收到消息时，会把 JSON 反序列化回 AnalysisTaskMessage。
     */
    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 创建 RabbitTemplate。
     *
     * RabbitTemplate 是生产者发送消息的核心工具。
     * ConnectionFactory 用于创建连接，连接后创建通道，通道后发送消息。
     * setMandatory(true) 表示如果消息无法路由到任何队列，会触发 return 机制，避免消息静默丢失。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jacksonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jacksonMessageConverter);
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }

    /**
     * 创建 RabbitMQ 消费者监听容器工厂。
     *
     * @RabbitListener 会使用这个工厂创建消费者容器。
     * MANUAL ack 表示消费者处理成功后手动确认消息；处理失败时可以手动 nack 到死信队列。
     * setAutoStartup(true) 表示容器启动时自动监听消息。
     * setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL) 表示手动确认消息。
     * nack 表示拒绝消息，不重新入队。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
