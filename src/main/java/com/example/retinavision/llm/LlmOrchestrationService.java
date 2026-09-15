package com.example.retinavision.llm;

public interface LlmOrchestrationService {
    LlmGenerationResult generateJson(String templateCode, String sanitizedUserContext);
}
