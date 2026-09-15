package com.example.retinavision.llm;

public enum PromptScenario {
    REPORT_DRAFT_GENERATION,
    RAG_KNOWLEDGE_CHAT,
    CASE_TREND_SUMMARY;

    public static PromptScenario fromTemplateCode(String templateCode) {
        try {
            return valueOf(templateCode);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new LlmException("Unsupported Prompt template code: " + templateCode);
        }
    }
}
