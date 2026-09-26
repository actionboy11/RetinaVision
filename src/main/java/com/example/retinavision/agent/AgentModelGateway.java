package com.example.retinavision.agent;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface AgentModelGateway {
    String generate(String systemPrompt, List<AgentConversationMessage> messages, ToolCallback[] tools);
}
