package com.example.retinavision.rag;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleEmbeddingClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", this::handleEmbedding);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsOpenAiCompatibleEmbeddingRequestAndParsesVector() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("embedding-key");
        properties.setModel("text-embedding-v4");
        properties.setDimension(4);
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(5));

        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties);

        float[] vector = client.embed("视网膜血管面积比是什么意思");

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        assertThat(authorization.get()).isEqualTo("Bearer embedding-key");
        assertThat(requestBody.get())
                .contains("\"model\":\"text-embedding-v4\"")
                .contains("\"input\":\"视网膜血管面积比是什么意思\"");
    }

    private void handleEmbedding(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, """
                {"data":[{"embedding":[0.1,0.2,0.3,0.4]}]}
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
