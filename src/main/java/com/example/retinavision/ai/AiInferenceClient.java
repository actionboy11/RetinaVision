package com.example.retinavision.ai;

import com.example.retinavision.ai.dto.AiInferenceResponse;

import java.nio.file.Path;

public interface AiInferenceClient {
    AiInferenceResponse segment(Path imagePath, String originalFilename, String contentType);

    byte[] downloadMask(String maskUrl);
}

