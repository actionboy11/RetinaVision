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
    // AI 服务是否可访问
    private boolean reachable;
    // AI 服务是否准备好处理请求
    private boolean ready;
    private boolean modelLoaded;
    private String modelName;
    private String modelVersion;
    private String device;
    // AI 服务是否正在处理请求
    private boolean busy;
    private OffsetDateTime startedAt;
    private Long totalRequests;
    private Long successCount;
    private Long failureCount;
    private Long lastInferenceTimeMs;
    private OffsetDateTime lastSuccessAt;
    private String lastError;
}
