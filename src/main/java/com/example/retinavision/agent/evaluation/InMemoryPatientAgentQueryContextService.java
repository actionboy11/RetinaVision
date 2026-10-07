package com.example.retinavision.agent.evaluation;

import com.example.retinavision.agent.PatientAgentQueryContextService;
import com.example.retinavision.agent.PatientAgentQueryContextSnapshot;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPatientAgentQueryContextService implements PatientAgentQueryContextService {
    private final ConcurrentHashMap<Long, PatientAgentQueryContextSnapshot> contexts = new ConcurrentHashMap<>();

    @Override
    public Optional<PatientAgentQueryContextSnapshot> load(Long sessionId) {
        return Optional.ofNullable(contexts.get(sessionId));
    }

    @Override
    public void save(Long sessionId, PatientAgentQueryContextSnapshot context) {
        contexts.put(sessionId, context);
    }
}
