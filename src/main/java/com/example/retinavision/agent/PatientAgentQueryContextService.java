package com.example.retinavision.agent;

import java.util.Optional;

public interface PatientAgentQueryContextService {
    Optional<PatientAgentQueryContextSnapshot> load(Long sessionId);

    void save(Long sessionId, PatientAgentQueryContextSnapshot context);
}
