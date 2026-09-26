package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "retina.ai", name = "framework", havingValue = "legacy", matchIfMissing = true)
public class LegacyAgentModelGateway implements AgentModelGateway {
    @Override
    public String generate(String systemPrompt, List<AgentConversationMessage> messages, ToolCallback[] tools) {
        throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE,
                "智能助手需要启用 retina.ai.framework=spring-ai");
    }
}
