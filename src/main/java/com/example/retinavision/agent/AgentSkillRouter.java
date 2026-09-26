package com.example.retinavision.agent;

public interface AgentSkillRouter {
    AgentSkillRoute route(String question, AgentSkillCode currentSkill);
}
