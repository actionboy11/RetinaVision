package com.example.retinavision.service.governance.evaluation;

import com.example.retinavision.pojo.agent.PatientAgentQueryContextSnapshot;
import com.example.retinavision.service.agent.PatientAgentQueryContextService;


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
