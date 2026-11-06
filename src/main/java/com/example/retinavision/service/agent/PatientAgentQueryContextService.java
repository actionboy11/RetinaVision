package com.example.retinavision.service.agent;

import com.example.retinavision.pojo.agent.PatientAgentQueryContextSnapshot;

import java.util.Optional;

public interface PatientAgentQueryContextService {
    Optional<PatientAgentQueryContextSnapshot> load(Long sessionId);

    void save(Long sessionId, PatientAgentQueryContextSnapshot context);
}
