package com.example.retinavision.llm;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "retina.ai", name = "framework", havingValue = "spring-ai")
public class SpringAiLlmClient implements LlmClient {
    private final ChatModel chatModel;
    private final LlmProperties properties;

    public SpringAiLlmClient(ChatModel chatModel, LlmProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        requireConfigured();
        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(properties.getModel())
                    .temperature(properties.getTemperature())
                    .maxTokens(properties.getMaxTokens())
                    .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                    .build();
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ), options));
            String content = response == null || response.getResult() == null
                    ? null : response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                throw new LlmException("LLM service returned an empty response");
            }
            return content;
        } catch (LlmException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LlmException("LLM service call failed", exception);
        }
    }

    private void requireConfigured() {
        if (!properties.isEnabled()) {
            throw new LlmException("LLM draft generation is disabled");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LlmException("LLM API key is not configured");
        }
    }
}
