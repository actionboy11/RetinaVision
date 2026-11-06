package com.example.retinavision.pojo.agent;

public record AgentSkillDefinition(AgentSkillCode code,
                                   String name,
                                   String description,
                                   int version,
                                   String routingExamplesJson,
                                   String workflowPrompt) {
}
