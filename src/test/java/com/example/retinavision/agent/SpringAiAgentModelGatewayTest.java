package com.example.retinavision.agent;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiAgentModelGatewayTest {

    @Test
    void returnsFinalAssistantTextWithoutExecutingTools() {
        ChatModel model = mock(ChatModel.class);
        ToolCallingManager manager = mock(ToolCallingManager.class);
        when(model.call(any(Prompt.class))).thenReturn(response("只读查询完成", List.of()));

        String answer = new SpringAiAgentModelGateway(model, manager, properties(), 4, 6)
                .generate("system", List.of(new AgentConversationMessage("USER", "查询任务")),
                        new ToolCallback[0]);

        assertThat(answer).isEqualTo("只读查询完成");
        verify(manager, never()).executeToolCalls(any(), any());
    }

    @Test
    void rejectsAResponseThatExceedsToolCallLimit() {
        ChatModel model = mock(ChatModel.class);
        ToolCallingManager manager = mock(ToolCallingManager.class);
        List<AssistantMessage.ToolCall> calls = IntStream.range(0, 7)
                .mapToObj(index -> new AssistantMessage.ToolCall("id-" + index, "function", "tool", "{}"))
                .toList();
        when(model.call(any(Prompt.class))).thenReturn(response("", calls));

        SpringAiAgentModelGateway gateway = new SpringAiAgentModelGateway(model, manager, properties(), 4, 6);

        assertThatThrownBy(() -> gateway.generate("system",
                List.of(new AgentConversationMessage("USER", "查询")), new ToolCallback[0]))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("工具调用次数");
        verify(manager, never()).executeToolCalls(any(), any());
    }

    private ChatResponse response(String content, List<AssistantMessage.ToolCall> calls) {
        AssistantMessage message = AssistantMessage.builder().content(content).toolCalls(calls).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private LlmProperties properties() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("qwen-plus");
        return properties;
    }
}
