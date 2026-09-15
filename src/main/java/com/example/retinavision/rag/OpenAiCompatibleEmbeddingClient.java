package com.example.retinavision.rag;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private final EmbeddingProperties properties;
    private final RestClient restClient;
    private final ObjectMapper json = new ObjectMapper();

    public OpenAiCompatibleEmbeddingClient(EmbeddingProperties properties) {
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
    public float[] embed(String text) {
        if (!properties.isEnabled()) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Embedding service is disabled");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Embedding API key is not configured");
        }
        try {
            String response = restClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", properties.getModel(), "input", text))
                    .retrieve()
                    .body(String.class);
            JsonNode embedding = json.readTree(response).path("data").path(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Embedding service returned an empty vector");
            }
            List<Float> values = json.convertValue(embedding,
                    json.getTypeFactory().constructCollectionType(List.class, Float.class));
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i);
            }
            if (properties.getDimension() > 0 && vector.length != properties.getDimension()) {
                throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Embedding vector dimension mismatch");
            }
            return vector;
        } catch (BaseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Embedding service call failed");
        }
    }

    private static String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("retina.embedding.base-url cannot be blank");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
