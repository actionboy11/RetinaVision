package com.example.retinavision.agent;

import java.util.List;

public record PatientAgentSkillResult(AgentSkillCode skillCode,
                                      int skillVersion,
                                      double confidence,
                                      String answer,
                                      AgentStructuredData data,
                                      AgentPagination pagination,
                                      List<AgentAction> actions) {
    public PatientAgentSkillResult(AgentSkillCode skillCode, double confidence, String answer,
                                   AgentStructuredData data, AgentPagination pagination,
                                   List<AgentAction> actions) {
        this(skillCode, 1, confidence, answer, data, pagination, actions);
    }
}
