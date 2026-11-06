package com.example.retinavision.application.agent.context.api;

import com.example.retinavision.pojo.agent.AgentQueryContextSnapshot;

import java.util.Optional;

public interface AgentQueryContextService {
    Optional<AgentQueryContextSnapshot> load(Long sessionId);

    void save(Long sessionId, AgentQueryContextSnapshot context);
}
