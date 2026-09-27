package com.example.retinavision.agent;

public record AgentSkillDefinition(AgentSkillCode code,
                                   String name,
                                   String description,
                                   int version,
                                   String routingExamplesJson,
                                   String workflowPrompt) {
}
