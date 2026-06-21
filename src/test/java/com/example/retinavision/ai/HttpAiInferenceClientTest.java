package com.example.retinavision.ai;

import com.example.retinavision.ai.dto.AiInferenceResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpAiInferenceClientTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicBoolean multipartReceived = new AtomicBoolean(false);
    private final AtomicReference<String> inferenceRequestId = new AtomicReference<>();
    private final AtomicReference<String> artifactRequestId = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/inference/vessel-segmentation", this::handleInference);
        server.createContext("/v1/inference/image-quality-check", exchange -> respond(exchange, 200, "application/json", """
                {"resultType":"IMAGE_QUALITY_CHECK","resultJson":{"grade":"PASS","score":88.5,"metrics":{"sharpness":0.9},"reasons":[]},"modelName":"retinavision-rule-quality","modelVersion":"1.0.0","processingTimeMs":8}
                """.getBytes(StandardCharsets.UTF_8)));
        server.createContext("/v1/artifacts/abc123/mask", exchange -> {
            artifactRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
            respond(exchange, 200, "image/png", "png-mask".getBytes(StandardCharsets.UTF_8));
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void uploadsImageParsesResponseAndDownloadsMask() throws IOException {
        Path image = Files.createTempFile("retina", ".png");
        Files.writeString(image, "image-bytes");
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(5));
        HttpAiInferenceClient client = new HttpAiInferenceClient(properties);

        AiInferenceResponse response = client.segment(image, "retina.png", "image/png", "task-100-attempt-1");
        byte[] mask = client.downloadMask(response.getMaskUrl(), "task-100-attempt-1");

        assertThat(multipartReceived).isTrue();
        assertThat(response.getInferenceId()).isEqualTo("abc123");
        assertThat(response.getResultType()).isEqualTo("VESSEL_SEGMENTATION");
        assertThat(response.getResultJson()).containsEntry("vesselAreaRatio", 0.25);
        assertThat(response.getModelName()).isEqualTo("FSCNet_Final_DMI");
        assertThat(mask).isEqualTo("png-mask".getBytes(StandardCharsets.UTF_8));
        assertThat(inferenceRequestId.get()).isEqualTo("task-100-attempt-1");
        assertThat(artifactRequestId.get()).isEqualTo("task-100-attempt-1");
    }

    @Test
    void rejectsUntrustedArtifactUrlWithStableFailureCategory() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl(baseUrl);
        HttpAiInferenceClient client = new HttpAiInferenceClient(properties);

        assertThatThrownBy(() -> client.downloadMask("https://evil.example/mask.png", "request-1"))
                .isInstanceOf(AiInferenceException.class)
                .extracting("category")
                .isEqualTo(AiFailureCategory.UNTRUSTED_ARTIFACT_URL);
    }

    @Test
    void callsQualityEndpointAndParsesStableContract() throws IOException {
        Path image = Files.createTempFile("retina-quality", ".png");
        Files.writeString(image, "image-bytes");
        AiServiceProperties properties = new AiServiceProperties(); properties.setBaseUrl(baseUrl);
        AiInferenceResponse response = new HttpAiInferenceClient(properties)
                .checkQuality(image, "retina.png", "image/png", "quality-1");
        assertThat(response.getResultType()).isEqualTo("IMAGE_QUALITY_CHECK");
        assertThat(response.getResultJson()).containsEntry("grade", "PASS").containsEntry("score", 88.5);
    }

    private void handleInference(HttpExchange exchange) throws IOException {
        inferenceRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
        multipartReceived.set(
                "POST".equals(exchange.getRequestMethod())
                        && contentType != null
                        && contentType.startsWith("multipart/form-data")
                        && body.contains("filename=\"retina.png\"")
                        && body.contains("image-bytes")
        );
        String json = """
                {
                  "inferenceId":"abc123",
                  "resultType":"VESSEL_SEGMENTATION",
                  "resultJson":{"vesselAreaRatio":0.25,"processingTimeMs":12,"modelVersion":"v1","conclusion":"ok"},
                  "modelName":"FSCNet_Final_DMI",
                  "modelVersion":"v1",
                  "processingTimeMs":12,
                  "maskUrl":"/v1/artifacts/abc123/mask"
                }
                """;
        respond(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
