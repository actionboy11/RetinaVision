package com.example.retinavision.agent;

import java.util.List;

public record PatientAgentQueryContextSnapshot(AgentSkillCode currentSkill,
                                               boolean reuploadOnly,
                                               boolean signedReportOnly,
                                               int page,
                                               int pageSize,
                                               long total,
                                               Long selectedCaseId,
                                               Long selectedResultId,
                                               Integer selectedReportVersion,
                                               List<PatientAgentReference> references) {
    public PatientAgentQueryContextSnapshot {
        if (references == null) references = List.of();
        references = List.copyOf(references.stream().limit(10).toList());
        page = Math.max(page, 1);
        pageSize = Math.max(1, Math.min(pageSize, 10));
    }
}
