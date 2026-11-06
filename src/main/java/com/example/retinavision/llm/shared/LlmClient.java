package com.example.retinavision.llm.shared;

public interface LlmClient {
    String generateJson(String systemPrompt, String userPrompt);
}
