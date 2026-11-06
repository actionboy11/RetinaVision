package com.example.retinavision.llm.shared;

public record LlmGenerationResult(
        String content,
        String templateCode,
        int templateVersion,
        String provider,
        String model,
        long latencyMs) {
}
