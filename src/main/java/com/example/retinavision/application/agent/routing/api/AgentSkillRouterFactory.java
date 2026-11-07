package com.example.retinavision.application.agent.routing.api;

import com.example.retinavision.llm.shared.LlmOrchestrationService;

public interface AgentSkillRouterFactory {
    AgentSkillRouter create(LlmOrchestrationService llm);
}
