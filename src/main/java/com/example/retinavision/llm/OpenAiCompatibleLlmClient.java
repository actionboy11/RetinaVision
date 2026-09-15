package com.example.retinavision.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
//
public class OpenAiCompatibleLlmClient implements LlmClient {
    private final LlmProperties properties;
    private final RestClient restClient;
    private final ObjectMapper json = new ObjectMapper();

    public OpenAiCompatibleLlmClient(LlmProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.isEnabled() ? requireBaseUrl(properties.getBaseUrl()) : "http://127.0.0.1")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            throw new LlmException("LLM draft generation is disabled");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LlmException("LLM API key is not configured");
        }
        //
        try {
            Map<String, Object> request = Map.of(
                    "model", properties.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", properties.getTemperature(),
                    "max_tokens", properties.getMaxTokens(),
                    "response_format", Map.of("type", "json_object"),
                    "stream", false
            );
            String response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            // extractContect() 表示从 LLM 服务返回的 JSON 中提取出最终的文本内容。
            //isBlank() 表示检查提取出的文本内容是否为空或仅包含空白字符。
            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                throw new LlmException("LLM service returned an empty response");
            }
            return content;
        } catch (LlmException exception) {
            throw exception;
        } catch (HttpClientErrorException exception) {
            throw new LlmException("LLM service rejected the request", exception);
        } catch (HttpServerErrorException exception) {
            throw new LlmException("LLM service is temporarily unavailable", exception);
        } catch (ResourceAccessException exception) {
            throw new LlmException("LLM service connection failed or timed out", exception);
        } catch (Exception exception) {
            throw new LlmException("LLM service call failed", exception);
        }
    }

    private String extractContent(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            // 解析 LLM 服务返回的 JSON 响应，提取出最终的文本内容。
            //JSON 解析器 Jackson 的 ObjectMapper 类提供了 readTree() 方法，可以将 JSON 字符串解析为 JsonNode 对象。
            // jsonNode 是 Jackson 的 JSON 树模型中的一个节点，表示 JSON 数据结构中的一个元素。
            JsonNode root = json.readTree(response);
            // 通过路径 "choices" 获取到 LLM 服务返回的候选结果数组。
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            // 通过路径 "message" 获取到候选结果中的消息对象，再通过路径 "content" 获取到最终的文本内容。
            return choices.get(0).path("message").path("content").asText(null);
        } catch (Exception exception) {
            throw new LlmException("LLM service returned an invalid response", exception);
        }
    }

    private static String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("retina.llm.base-url cannot be blank");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
