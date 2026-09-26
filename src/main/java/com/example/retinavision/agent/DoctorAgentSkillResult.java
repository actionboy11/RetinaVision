package com.example.retinavision.agent;

import java.util.List;

public record DoctorAgentSkillResult(AgentSkillCode skillCode,
                                     int skillVersion,
                                     double confidence,
                                     String answer,
                                     AgentStructuredData data,
                                     AgentPagination pagination,
                                     List<AgentAction> actions) {
    public DoctorAgentSkillResult(AgentSkillCode skillCode, double confidence, String answer,
                                  AgentStructuredData data, AgentPagination pagination,
                                  List<AgentAction> actions) {
        this(skillCode, 1, confidence, answer, data, pagination, actions);
    }
}
