package com.example.retinavision.llm.shared;

import com.example.retinavision.exception.llm.LlmException;
import com.example.retinavision.pojo.governance.entity.PromptTemplateVersionEntity;
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
