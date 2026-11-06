package com.example.retinavision.application.agent.context.api;

import com.example.retinavision.pojo.agent.PatientAgentQueryContextSnapshot;

import java.util.Optional;

public interface PatientAgentQueryContextService {
    Optional<PatientAgentQueryContextSnapshot> load(Long sessionId);

    void save(Long sessionId, PatientAgentQueryContextSnapshot context);
}
