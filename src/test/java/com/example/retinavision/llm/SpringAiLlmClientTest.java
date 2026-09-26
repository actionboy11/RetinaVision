package com.example.retinavision.llm;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiLlmClientTest {

    @Test
    void requestsJsonAndReturnsAssistantContent() {
        ChatModel model = mock(ChatModel.class);
        LlmProperties properties = properties();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("{\"answer\":\"ok\"}"))
        )));

        String result = new SpringAiLlmClient(model, properties)
                .generateJson("system rules", "sanitized context");

        assertThat(result).isEqualTo("{\"answer\":\"ok\"}");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText()).isEqualTo("system rules");
        assertThat(prompt.getValue().getUserMessage().getText()).isEqualTo("sanitized context");
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
        assertThat(options.getModel()).isEqualTo("qwen-plus");
    }

    private LlmProperties properties() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("qwen-plus");
        properties.setTemperature(0.2);
        properties.setMaxTokens(800);
        return properties;
    }
}
