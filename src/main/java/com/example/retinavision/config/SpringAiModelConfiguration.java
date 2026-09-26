package com.example.retinavision.config;

import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.rag.EmbeddingProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "retina.ai", name = "framework", havingValue = "spring-ai")
public class SpringAiModelConfiguration {

    @Bean
    @ConditionalOnMissingBean(ToolCallingManager.class)
    ToolCallingManager retinaToolCallingManager() {
        return ToolCallingManager.builder().build();
    }

    @Bean
    @Primary
    ChatModel retinaChatModel(LlmProperties properties) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl(properties.isEnabled(), properties.getBaseUrl()))
                .apiKey(apiKey(properties.isEnabled(), properties.getApiKey()))
                .completionsPath("/chat/completions")
                .restClientBuilder(restClientBuilder(properties.getConnectTimeout().toMillis(),
                        properties.getReadTimeout().toMillis()))
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    @Bean
    @Primary
    EmbeddingModel retinaEmbeddingModel(EmbeddingProperties properties) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl(properties.isEnabled(), properties.getBaseUrl()))
                .apiKey(apiKey(properties.isEnabled(), properties.getApiKey()))
                .embeddingsPath("/embeddings")
                .restClientBuilder(restClientBuilder(properties.getConnectTimeout().toMillis(),
                        properties.getReadTimeout().toMillis()))
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.getModel())
                .dimensions(properties.getDimension())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options,
                RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build(), ObservationRegistry.NOOP);
    }

    private RestClient.Builder restClientBuilder(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(connectTimeoutMs, Integer.MAX_VALUE));
        factory.setReadTimeout((int) Math.min(readTimeoutMs, Integer.MAX_VALUE));
        return RestClient.builder().requestFactory(factory);
    }

    private String baseUrl(boolean enabled, String configured) {
        if (!enabled) {
            return "http://127.0.0.1";
        }
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("AI base URL cannot be blank when Spring AI is enabled");
        }
        return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }

    private String apiKey(boolean enabled, String configured) {
        return enabled && configured != null && !configured.isBlank() ? configured : "disabled";
    }
}
