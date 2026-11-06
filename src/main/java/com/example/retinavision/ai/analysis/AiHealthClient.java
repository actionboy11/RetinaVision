package com.example.retinavision.ai.analysis;

import com.example.retinavision.ai.analysis.dto.AiHealthResponse;

// AI 健康检查客户端接口，用于检查 AI 服务的健康状态
public interface AiHealthClient {
    AiHealthResponse checkHealth();
}
