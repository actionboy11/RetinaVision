package com.example.retinavision.ai.analysis;

import com.example.retinavision.ai.analysis.dto.AiInferenceResponse;

import java.nio.file.Path;

public interface AiInferenceClient {
    AiInferenceResponse segment(Path imagePath, String originalFilename, String contentType, String requestId);

    AiInferenceResponse checkQuality(Path imagePath, String originalFilename, String contentType, String requestId);

    byte[] downloadMask(String maskUrl, String requestId);
}

