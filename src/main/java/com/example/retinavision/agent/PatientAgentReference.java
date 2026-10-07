package com.example.retinavision.agent;

public record PatientAgentReference(Long caseId, String caseNo, Long resultId, Integer reportVersion) {
}
