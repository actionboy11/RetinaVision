package com.example.retinavision.agent;

public interface AgentSkillRouter {
    AgentSkillRoute route(String question, AgentSkillCode currentSkill);

    default AgentSkillRoute route(String question, AgentSkillCode currentSkill,
                                  java.util.List<AgentSkillDefinition> availableSkills) {
        return route(question, currentSkill);
    }
}
