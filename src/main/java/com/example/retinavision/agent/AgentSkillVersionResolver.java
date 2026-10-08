package com.example.retinavision.agent;

public interface AgentSkillVersionResolver {
    AgentSkillRuntimeVersion resolve(Long sessionId, AgentSkillCode skillCode);
}
