package com.example.retinavision.ai;

import com.example.retinavision.ai.dto.AiHealthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpAiHealthClient implements AiHealthClient {

    private final RestClient restClient;

    @Autowired
    public HttpAiHealthClient(AiServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getHealthConnectTimeout());
        requestFactory.setReadTimeout(properties.getHealthReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    HttpAiHealthClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    // 检查 AI 服务的健康状态
    public AiHealthResponse checkHealth() {
        try {
            AiHealthResponse response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(AiHealthResponse.class);
            if (response == null) {
                return down("AI 健康检查失败：响应为空");
            }
            response.setReachable(true);
            return response;
        } catch (Exception exception) {
            return down("AI 健康检查失败：" + exception.getClass().getSimpleName());
        }
    }

    private AiHealthResponse down(String error) {
        return AiHealthResponse.builder()
                .status("DOWN")
                .reachable(false)
                .ready(false)
                .modelLoaded(false)
                .busy(false)
                .lastError(error)
                .build();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("retina.ai.base-url 不能为空");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
