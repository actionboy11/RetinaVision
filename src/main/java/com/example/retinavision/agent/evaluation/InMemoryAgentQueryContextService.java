package com.example.retinavision.agent.evaluation;

import com.example.retinavision.agent.AgentQueryContextService;
import com.example.retinavision.agent.AgentQueryContextSnapshot;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAgentQueryContextService implements AgentQueryContextService {
    private final ConcurrentHashMap<Long, AgentQueryContextSnapshot> contexts = new ConcurrentHashMap<>();

    @Override
    public Optional<AgentQueryContextSnapshot> load(Long sessionId) {
        return Optional.ofNullable(contexts.get(sessionId));
    }

    @Override
    public void save(Long sessionId, AgentQueryContextSnapshot context) {
        contexts.put(sessionId, context);
    }
}
