package com.example.retinavision.llm;

public interface LlmClient {
    String generateJson(String systemPrompt, String userPrompt);
}
