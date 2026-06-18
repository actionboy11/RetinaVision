package com.example.retinavision.ai.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AiInferenceResponse {
    private String inferenceId;
    private String resultType;
    private Map<String, Object> resultJson;
    private String modelName;
    private String modelVersion;
    private Integer processingTimeMs;
    private String maskUrl;
}

