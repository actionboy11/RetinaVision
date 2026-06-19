package com.example.retinavision.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component //@Component 注解将类标记为 Spring 组件，允许在其他组件中注入该类的实例
@ConfigurationProperties(prefix = "retina.mq")
public class RabbitMqProperties {

    private String analysisExchange;

    private String analysisRoutingKey;

    private String analysisQueue;

    private String analysisRetryExchange;

    private String analysisRetryRoutingKey;

    private String analysisRetryQueue;

    private Long analysisRetryDelayMs = 10_000L;

    private Integer analysisMaxAutoRetries = 3;

    private String analysisDeadLetterExchange;

    private String analysisDeadLetterRoutingKey;

    private String analysisDeadLetterQueue;
}
