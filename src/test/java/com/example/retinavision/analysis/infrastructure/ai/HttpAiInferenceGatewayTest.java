package com.example.retinavision.analysis.infrastructure.ai;

import com.example.retinavision.ai.AiInferenceClient;
import com.example.retinavision.ai.dto.AiInferenceResponse;
import com.example.retinavision.analysis.application.model.InferenceOutput;
import com.example.retinavision.analysis.application.model.SourceImage;
import com.example.retinavision.analysis.infrastructure.storage.LocalArtifactStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpAiInferenceGatewayTest {

    @TempDir
    Path tempDir;

    private final AiInferenceClient client = mock(AiInferenceClient.class);
    private Path sourcePath;
    private SourceImage sourceImage;
    private HttpAiInferenceGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        Path imageRoot = Files.createDirectories(tempDir.resolve("images"));
        Path resultRoot = Files.createDirectories(tempDir.resolve("results"));
        sourcePath = imageRoot.resolve("cases/10/source.png");
        Files.createDirectories(sourcePath.getParent());
        Files.write(sourcePath, new byte[]{1});
        sourceImage = new SourceImage(
                20L, "source.png", "image/png", "cases/10/source.png");
        gateway = new HttpAiInferenceGateway(
                client, new LocalArtifactStore(imageRoot.toString(), resultRoot.toString()));
    }

    @Test
    void mapsQualityProviderResponseToImmutableApplicationOutput() {
        AiInferenceResponse response = response(null);
        when(client.checkQuality(sourcePath, "source.png", "image/png", "trace-20"))
                .thenReturn(response);

        InferenceOutput output = gateway.checkQuality(sourceImage, "trace-20");

        assertThat(output.resultJson()).containsEntry("grade", "PASS");
        assertThat(output.modelName()).isEqualTo("quality-model");
        assertThat(output.modelVersion()).isEqualTo("v1");
        assertThat(output.processingTimeMs()).isEqualTo(7);
        assertThat(output.maskUrl()).isNull();
        assertThat(output).isNotInstanceOf(AiInferenceResponse.class);
        assertThat(output.getClass().getRecordComponents())
                .extracting(component -> component.getType().getName())
                .noneMatch(name -> name.equals(Path.class.getName())
                        || name.equals(AiInferenceResponse.class.getName()));
    }

    @Test
    void resolvesSourceForSegmentationAndDelegatesMaskDownloadUnchanged() {
        AiInferenceResponse response = response("/v1/artifacts/mask-20");
        when(client.segment(sourcePath, "source.png", "image/png", "trace-20"))
                .thenReturn(response);
        when(client.downloadMask("/v1/artifacts/mask-20", "trace-20"))
                .thenReturn(new byte[]{3, 2, 1});

        InferenceOutput output = gateway.segment(sourceImage, "trace-20");
        byte[] bytes = gateway.downloadMask(output.maskUrl(), "trace-20");

        assertThat(output.maskUrl()).isEqualTo("/v1/artifacts/mask-20");
        assertThat(bytes).containsExactly(3, 2, 1);
        verify(client).segment(sourcePath, "source.png", "image/png", "trace-20");
        verify(client).downloadMask("/v1/artifacts/mask-20", "trace-20");
    }

    private AiInferenceResponse response(String maskUrl) {
        AiInferenceResponse response = new AiInferenceResponse();
        response.setResultJson(Map.of("grade", "PASS", "score", 88.5));
        response.setModelName("quality-model");
        response.setModelVersion("v1");
        response.setProcessingTimeMs(7);
        response.setMaskUrl(maskUrl);
        return response;
    }
}
