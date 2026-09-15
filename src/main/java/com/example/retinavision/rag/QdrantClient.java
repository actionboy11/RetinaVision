package com.example.retinavision.rag;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class QdrantClient {
    private final QdrantProperties properties;
    private final RestClient restClient;
    private final ObjectMapper json = new ObjectMapper();

    public QdrantClient(QdrantProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(trim(properties.getBaseUrl()))
                .defaultHeader("api-key", properties.getApiKey() == null ? "" : properties.getApiKey())
                .build();
    }

    public void ensureCollection(int dimension) {
        ensureCollection(properties.getCollection(), dimension);
    }

    public void ensureCollection(String collection, int dimension) {
        requireCollection(collection);
        try {
            restClient.put()
                    .uri("/collections/{collection}", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("vectors", Map.of("size", dimension, "distance", "Cosine")))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict exception) {
            return;
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Qdrant collection could not be prepared");
        }
    }

    public void upsert(List<QdrantPoint> points) {
        upsert(properties.getCollection(), points);
    }

    public void upsert(String collection, List<QdrantPoint> points) {
        requireCollection(collection);
        try {
            List<Map<String, Object>> payload = points.stream()
                    .map(point -> Map.of(
                            "id", point.id(),
                            "vector", point.vector(),
                            "payload", Map.of(
                                    "documentId", point.documentId(),
                                    "chunkId", point.chunkId(),
                                    "documentTitle", point.documentTitle(),
                                    "source", point.source(),
                                    "category", point.category(),
                                    "status", point.status(),
                                    "text", point.text()
                            )
                    ))
                    .toList();
            restClient.put()
                    .uri("/collections/{collection}/points?wait=true", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("points", payload))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Qdrant points could not be upserted");
        }
    }

    public void deletePoints(List<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/collections/{collection}/points/delete", properties.getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("points", pointIds))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Qdrant points could not be deleted");
        }
    }

    public List<QdrantSearchHit> search(float[] vector, int limit) {
        return search(properties.getCollection(), vector, limit);
    }

    public List<QdrantSearchHit> search(String collection, float[] vector, int limit) {
        requireCollection(collection);
        try {
            String response = restClient.post()
                    .uri("/collections/{collection}/points/search", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "vector", vector,
                            "limit", limit,
                            "with_payload", true,
                            "score_threshold", properties.getScoreThreshold(),
                            "filter", Map.of(
                                    "should", List.of(
                                            Map.of(
                                            "key", "status",
                                            "match", Map.of("value", "ACTIVE")
                                            ),
                                            Map.of("is_empty", Map.of("key", "status"))
                                    )
                            )
                    ))
                    .retrieve()
                    .body(String.class);
            JsonNode results = json.readTree(response).path("result");
            List<QdrantSearchHit> hits = new ArrayList<>();
            if (!results.isArray()) {
                return hits;
            }
            for (JsonNode item : results) {
                JsonNode payload = item.path("payload");
                hits.add(new QdrantSearchHit(
                        item.path("id").asText(),
                        item.path("score").asDouble(),
                        payload.path("documentId").asLong(),
                        payload.path("chunkId").asLong(),
                        payload.path("documentTitle").asText(),
                        payload.path("source").asText(),
                        payload.path("text").asText()
                ));
            }
            return hits;
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Qdrant search failed");
        }
    }

    private void requireCollection(String collection) {
        if (collection == null || !collection.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid Qdrant collection name");
        }
    }

    private static String trim(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://127.0.0.1:6333";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
