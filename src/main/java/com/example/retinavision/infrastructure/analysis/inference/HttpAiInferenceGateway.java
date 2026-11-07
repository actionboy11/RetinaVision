package com.example.retinavision.infrastructure.analysis.inference;

import com.example.retinavision.ai.analysis.AiInferenceClient;
import com.example.retinavision.ai.analysis.dto.AiInferenceResponse;
import com.example.retinavision.pojo.analysis.dto.InferenceOutput;
import com.example.retinavision.pojo.analysis.dto.SourceImage;
import com.example.retinavision.port.analysis.AiInferencePort;
import com.example.retinavision.infrastructure.analysis.storage.LocalArtifactStore;

import java.nio.file.Path;

public final class HttpAiInferenceGateway implements AiInferencePort {

    private final AiInferenceClient client;
    private final LocalArtifactStore artifactStore;

    public HttpAiInferenceGateway(
            AiInferenceClient client,
            LocalArtifactStore artifactStore) {
        this.client = client;
        this.artifactStore = artifactStore;
    }

    @Override
    public InferenceOutput checkQuality(SourceImage image, String traceId) {
        Path source = artifactStore.resolveSource(image);
        return toOutput(client.checkQuality(
                source, image.originalFilename(), image.contentType(), traceId));
    }

    @Override
    public InferenceOutput segment(SourceImage image, String traceId) {
        Path source = artifactStore.resolveSource(image);
        return toOutput(client.segment(
                source, image.originalFilename(), image.contentType(), traceId));
    }

    @Override
    public byte[] downloadMask(String artifactUrl, String traceId) {
        return client.downloadMask(artifactUrl, traceId);
    }

    private InferenceOutput toOutput(AiInferenceResponse response) {
        return new InferenceOutput(
                response.getResultJson(),
                response.getModelName(),
                response.getModelVersion(),
                response.getProcessingTimeMs(),
                response.getMaskUrl());
    }
}
