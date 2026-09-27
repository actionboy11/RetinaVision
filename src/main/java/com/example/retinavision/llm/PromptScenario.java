package com.example.retinavision.llm;

public enum PromptScenario {
    REPORT_DRAFT_GENERATION,
    RAG_KNOWLEDGE_CHAT,
    CASE_TREND_SUMMARY,
    CLINICAL_ASSISTANT_AGENT,
    PATIENT_ASSISTANT_AGENT,
    AGENT_SKILL_ROUTER;

    public static PromptScenario fromTemplateCode(String templateCode) {
        try {
            return valueOf(templateCode);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new LlmException("Unsupported Prompt template code: " + templateCode);
        }
    }
}
