package com.example.retinavision.llm;

public interface LlmOrchestrationService {
    LlmGenerationResult generateJson(String templateCode, String sanitizedUserContext);

    default LlmGenerationResult generateJsonForEvaluation(String templateCode, Long versionId,
                                                          String sanitizedUserContext, Long evaluationRunId) {
        throw new UnsupportedOperationException("Version-specific evaluation is not supported");
    }
}
