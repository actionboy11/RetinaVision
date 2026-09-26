package com.example.retinavision.llm;

import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import org.springframework.stereotype.Component;

@Component
public class PromptRenderService {
    public RenderedPrompt render(PromptTemplateVersionEntity version, String sanitizedUserContext) {
        if (version == null || version.getSystemPrompt() == null || version.getSystemPrompt().isBlank()) {
            throw new LlmException("Prompt template system prompt is empty");
        }
        if (sanitizedUserContext == null || sanitizedUserContext.isBlank()) {
            throw new LlmException("LLM user context is empty");
        }
        return new RenderedPrompt(version.getSystemPrompt(), sanitizedUserContext);
    }
}
