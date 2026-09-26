package com.example.retinavision.agent;

import java.util.Optional;

public interface AgentQueryContextService {
    Optional<AgentQueryContextSnapshot> load(Long sessionId);

    void save(Long sessionId, AgentQueryContextSnapshot context);
}
