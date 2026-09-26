package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "retina.ai", name = "framework", havingValue = "spring-ai")
public class SpringAiAgentModelGateway implements AgentModelGateway {
    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final LlmProperties properties;
    private final int maxRounds;
    private final int maxToolCalls;

    public SpringAiAgentModelGateway(ChatModel chatModel,
                                     ToolCallingManager toolCallingManager,
                                     LlmProperties properties,
                                     @Value("${retina.agent.max-rounds:4}") int maxRounds,
                                     @Value("${retina.agent.max-tool-calls:6}") int maxToolCalls) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.properties = properties;
        this.maxRounds = maxRounds;
        this.maxToolCalls = maxToolCalls;
    }

    @Override
    public String generate(String systemPrompt, List<AgentConversationMessage> messages, ToolCallback[] tools) {
        requireConfigured();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .parallelToolCalls(false)
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(false)
                .build();
        Prompt prompt = new Prompt(toMessages(systemPrompt, messages), options);
        try {
            ChatResponse response = chatModel.call(prompt);
            int rounds = 0;
            int callCount = 0;
            while (response != null && response.hasToolCalls()) {
                int requested = response.getResult().getOutput().getToolCalls().size();
                if (rounds >= maxRounds || callCount + requested > maxToolCalls) {
                    throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE,
                            "智能助手工具调用次数已达到安全上限，请缩小问题范围后重试");
                }
                ToolExecutionResult execution = toolCallingManager.executeToolCalls(prompt, response);
                prompt = new Prompt(execution.conversationHistory(), options);
                response = chatModel.call(prompt);
                rounds++;
                callCount += requested;
            }
            String content = response == null || response.getResult() == null
                    ? null : response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "智能助手未返回有效内容");
            }
            return content;
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE,
                    "当前模型不支持所需的工具调用协议或服务暂时不可用");
        }
    }

    private List<Message> toMessages(String systemPrompt, List<AgentConversationMessage> history) {
        List<Message> result = new ArrayList<>();
        result.add(new SystemMessage(systemPrompt));
        for (AgentConversationMessage message : history) {
            if ("ASSISTANT".equalsIgnoreCase(message.role())) {
                result.add(new AssistantMessage(message.content()));
            } else {
                result.add(new UserMessage(message.content()));
            }
        }
        return result;
    }

    private void requireConfigured() {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "请先配置可用的大模型服务");
        }
    }
}
