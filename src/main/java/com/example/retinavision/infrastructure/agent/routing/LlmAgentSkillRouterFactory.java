package com.example.retinavision.infrastructure.agent.routing;

import com.example.retinavision.application.agent.routing.AgentSkillRegistry;
import com.example.retinavision.application.agent.routing.api.AgentSkillRouter;
import com.example.retinavision.application.agent.routing.api.AgentSkillRouterFactory;
import com.example.retinavision.llm.shared.LlmOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class LlmAgentSkillRouterFactory implements AgentSkillRouterFactory {
    private final ObjectMapper json;
    private final AgentSkillRegistry registry;

    public LlmAgentSkillRouterFactory(ObjectMapper json, AgentSkillRegistry registry) {
        this.json = json;
        this.registry = registry;
    }

    @Override
    public AgentSkillRouter create(LlmOrchestrationService llm) {
        return new LlmAgentSkillRouter(llm, json, registry);
    }
}
