package com.example.retinavision.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiHealthResponse {
    private String status;
    private boolean reachable;
    private boolean ready;
    private boolean modelLoaded;
    private String modelName;
    private String modelVersion;
    private String device;
    private boolean busy;
    private OffsetDateTime startedAt;
    private Long totalRequests;
    private Long successCount;
    private Long failureCount;
    private Long lastInferenceTimeMs;
    private OffsetDateTime lastSuccessAt;
    private String lastError;
}
