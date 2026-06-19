package com.example.retinavision.ai;

import com.example.retinavision.ai.dto.AiHealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAiHealthClientTest {

    @Test
    void mapsReadyPythonHealthResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://ai.test/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "status":"UP",
                          "ready":true,
                          "modelLoaded":true,
                          "modelName":"FSCNet_Final_DMI",
                          "modelVersion":"model_new-v1",
                          "device":"cpu",
                          "busy":false,
                          "startedAt":"2026-06-19T10:00:00Z",
                          "totalRequests":3,
                          "successCount":2,
                          "failureCount":1,
                          "lastInferenceTimeMs":120,
                          "lastSuccessAt":"2026-06-19T10:01:00Z",
                          "lastError":null
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpAiHealthClient client = new HttpAiHealthClient(builder.build());

        AiHealthResponse health = client.checkHealth();

        assertThat(health.isReachable()).isTrue();
        assertThat(health.getStatus()).isEqualTo("UP");
        assertThat(health.getModelName()).isEqualTo("FSCNet_Final_DMI");
        assertThat(health.getTotalRequests()).isEqualTo(3L);
        server.verify();
    }

    @Test
    void convertsProbeFailureToDownInsteadOfThrowing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://ai.test/health"))
                .andRespond(withServerError());
        HttpAiHealthClient client = new HttpAiHealthClient(builder.build());

        AiHealthResponse health = client.checkHealth();

        assertThat(health.isReachable()).isFalse();
        assertThat(health.isReady()).isFalse();
        assertThat(health.getStatus()).isEqualTo("DOWN");
        assertThat(health.getLastError()).contains("AI 健康检查失败");
        server.verify();
    }

    @Test
    void healthTimeoutDefaultsAreShorterThanInferenceTimeout() {
        AiServiceProperties properties = new AiServiceProperties();

        assertThat(properties.getHealthConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getHealthReadTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getHealthReadTimeout()).isLessThan(properties.getReadTimeout());
    }
}
