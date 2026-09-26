package com.example.retinavision.agent;

import java.util.Map;

public record AgentSkillRoute(AgentSkillCode skillCode,
                              double confidence,
                              Map<String, String> arguments,
                              AgentContextCommand command,
                              Integer selectedIndex) {
    public AgentSkillRoute(AgentSkillCode skillCode, double confidence, Map<String, String> arguments) {
        this(skillCode, confidence, arguments, AgentContextCommand.NONE, null);
    }
}
