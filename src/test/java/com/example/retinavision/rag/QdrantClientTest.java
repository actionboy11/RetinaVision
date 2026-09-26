package com.example.retinavision.rag;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> upsertBody = new AtomicReference<>();
    private final AtomicReference<String> searchBody = new AtomicReference<>();
    private final AtomicReference<String> deleteBody = new AtomicReference<>();
    private final AtomicReference<String> evaluationPath = new AtomicReference<>();
    private final AtomicInteger collectionStatus = new AtomicInteger(200);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/retina_knowledge_chunks", this::handleCollection);
        server.createContext("/collections/retina_knowledge_chunks/points/delete", this::handleDelete);
        server.createContext("/collections/retina_knowledge_chunks/points/search", this::handleSearch);
        server.createContext("/collections/retina_knowledge_chunks/points", this::handleUpsert);
        server.createContext("/collections/retina_rag_eval_v1", exchange -> {
            evaluationPath.set(exchange.getRequestURI().getPath());
            if (exchange.getRequestURI().getPath().endsWith("/points/search")) {
                handleSearch(exchange);
            } else if (exchange.getRequestURI().getPath().endsWith("/points")) {
                handleUpsert(exchange);
            } else {
                handleCollection(exchange);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createsCollectionUpsertsPointsDeletesPointsAndParsesSearchResults() {
        QdrantProperties properties = new QdrantProperties();
        properties.setBaseUrl(baseUrl);
        properties.setCollection("retina_knowledge_chunks");
        properties.setApiKey("qdrant-key");
        properties.setScoreThreshold(0.35);

        QdrantClient client = new QdrantClient(properties);
        client.ensureCollection(4);
        client.upsert(List.of(new QdrantPoint(
                "point-1",
                new float[]{0.1f, 0.2f, 0.3f, 0.4f},
                10L,
                20L,
                "视网膜血管知识",
                "指南",
                "MEDICAL_BASE",
                "ACTIVE",
                "血管面积比是辅助分析指标"
        )));
        client.deletePoints(List.of("point-1"));

        List<QdrantSearchHit> hits = client.search(new float[]{0.1f, 0.2f, 0.3f, 0.4f}, 3);

        assertThat(upsertBody.get())
                .contains("\"id\":\"point-1\"")
                .contains("\"documentId\":10")
                .contains("\"category\":\"MEDICAL_BASE\"")
                .contains("\"status\":\"ACTIVE\"");
        assertThat(deleteBody.get()).contains("\"point-1\"");
        assertThat(searchBody.get())
                .contains("\"limit\":3")
                .contains("\"score_threshold\":0.35")
                .contains("\"key\":\"status\"")
                .contains("\"value\":\"ACTIVE\"")
                .contains("\"is_empty\"");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo(20L);
        assertThat(hits.get(0).documentTitle()).isEqualTo("视网膜血管知识");
        assertThat(hits.get(0).score()).isEqualTo(0.91);
    }

    @Test
    void treatsExistingCollectionAsPrepared() {
        collectionStatus.set(409);
        QdrantProperties properties = new QdrantProperties();
        properties.setBaseUrl(baseUrl);
        properties.setCollection("retina_knowledge_chunks");

        QdrantClient client = new QdrantClient(properties);

        client.ensureCollection(4);
    }

    @Test
    void evaluationCollectionIsIsolatedFromLiveKnowledgeCollection() {
        QdrantProperties properties = new QdrantProperties();
        properties.setBaseUrl(baseUrl);
        properties.setCollection("retina_knowledge_chunks");
        QdrantClient client = new QdrantClient(properties);

        client.ensureCollection("retina_rag_eval_v1", 4);
        client.upsert("retina_rag_eval_v1", List.of(new QdrantPoint("point-1", new float[]{1, 0, 0, 0},
                10L, 20L, "评测片段", "合成", "FAQ", "ACTIVE", "仅用于评测")));
        client.search("retina_rag_eval_v1", new float[]{1, 0, 0, 0}, 5);

        assertThat(evaluationPath.get()).isEqualTo("/collections/retina_rag_eval_v1/points/search");
    }

    private void handleCollection(HttpExchange exchange) throws IOException {
        respond(exchange, collectionStatus.get(), "{\"result\":true}");
    }

    private void handleUpsert(HttpExchange exchange) throws IOException {
        upsertBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, "{\"result\":{\"operation_id\":1,\"status\":\"completed\"}}");
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        deleteBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, "{\"result\":{\"operation_id\":2,\"status\":\"completed\"}}");
    }

    private void handleSearch(HttpExchange exchange) throws IOException {
        searchBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, """
                {"result":[{"id":"point-1","score":0.91,"payload":{"documentId":10,"chunkId":20,"documentTitle":"视网膜血管知识","source":"指南","text":"血管面积比是辅助分析指标"}}]}
                """);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
