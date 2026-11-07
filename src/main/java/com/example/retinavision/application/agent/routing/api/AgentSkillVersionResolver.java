package com.example.retinavision.application.agent.routing.api;

import com.example.retinavision.pojo.agent.AgentSkillCode;
import com.example.retinavision.pojo.agent.AgentSkillRuntimeVersion;

public interface AgentSkillVersionResolver {
    AgentSkillRuntimeVersion resolve(Long sessionId, AgentSkillCode skillCode);
}
