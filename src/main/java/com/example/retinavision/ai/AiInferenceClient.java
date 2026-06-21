package com.example.retinavision.ai;

import com.example.retinavision.ai.dto.AiInferenceResponse;

import java.nio.file.Path;

public interface AiInferenceClient {
    AiInferenceResponse segment(Path imagePath, String originalFilename, String contentType, String requestId);

    AiInferenceResponse checkQuality(Path imagePath, String originalFilename, String contentType, String requestId);

    byte[] downloadMask(String maskUrl, String requestId);
}

