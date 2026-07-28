package com.example.retinavision.analysis.application.model;

import java.util.Map;

public record InferenceOutput(
        Map<String, Object> resultJson,
        String modelName,
        String modelVersion,
        Integer processingTimeMs,
        String maskUrl) {

    public InferenceOutput {
        resultJson = resultJson == null ? Map.of() : Map.copyOf(resultJson);
    }
}
