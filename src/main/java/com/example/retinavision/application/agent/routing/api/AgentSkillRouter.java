package com.example.retinavision.application.agent.routing.api;

import com.example.retinavision.pojo.agent.AgentSkillCode;
import com.example.retinavision.pojo.agent.AgentSkillDefinition;
import com.example.retinavision.pojo.agent.AgentSkillRoute;

public interface AgentSkillRouter {
    AgentSkillRoute route(String question, AgentSkillCode currentSkill);

    default AgentSkillRoute route(String question, AgentSkillCode currentSkill,
                                  java.util.List<AgentSkillDefinition> availableSkills) {
        return route(question, currentSkill);
    }
}
