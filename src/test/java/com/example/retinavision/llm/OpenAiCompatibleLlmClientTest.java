package com.example.retinavision.llm;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLlmClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", this::handleChat);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsOpenAiCompatibleJsonRequestAndParsesMessageContent() {
        LlmProperties properties = properties();
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties);

        String content = client.generateJson("system prompt", "user prompt");

        assertThat(content).isEqualTo("{\"findings\":\"ok\"}");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get())
                .contains("\"model\":\"qwen-plus\"")
                .contains("\"temperature\":0.2")
                .contains("\"max_tokens\":800")
                .contains("\"response_format\":{\"type\":\"json_object\"}")
                .contains("\"stream\":false")
                .contains("system prompt")
                .contains("user prompt");
    }

    @Test
    void hidesProviderErrorDetailsBehindReadableBusinessError() throws IOException {
        server.removeContext("/chat/completions");
        server.createContext("/chat/completions", exchange ->
                respond(exchange, 401, "application/json", "{\"error\":{\"message\":\"bad secret token\"}}"));

        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties());

        assertThatThrownBy(() -> client.generateJson("system", "user"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("LLM service rejected the request")
                .hasMessageNotContaining("bad secret token");
    }

    @Test
    void rejectsEmptyAssistantContent() throws IOException {
        server.removeContext("/chat/completions");
        server.createContext("/chat/completions", exchange ->
                respond(exchange, 200, "application/json", "{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));

        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties());

        assertThatThrownBy(() -> client.generateJson("system", "user"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("LLM service returned an empty response");
    }

    private LlmProperties properties() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setProvider("qwen");
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-key");
        properties.setModel("qwen-plus");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(5));
        properties.setTemperature(0.2);
        properties.setMaxTokens(800);
        return properties;
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, "application/json", """
                {"choices":[{"message":{"content":"{\\"findings\\":\\"ok\\"}"}}]}
                """);
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
