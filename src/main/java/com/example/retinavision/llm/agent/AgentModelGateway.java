package com.example.retinavision.llm.agent;

import com.example.retinavision.pojo.agent.AgentConversationMessage;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface AgentModelGateway {
    String generate(String systemPrompt, List<AgentConversationMessage> messages, ToolCallback[] tools);
}
