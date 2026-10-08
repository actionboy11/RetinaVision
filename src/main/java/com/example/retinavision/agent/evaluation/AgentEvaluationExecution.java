package com.example.retinavision.agent.evaluation;

import com.example.retinavision.agent.AgentAction;
import com.example.retinavision.agent.AgentPagination;
import com.example.retinavision.agent.AgentSkillCode;
import com.example.retinavision.agent.AgentStructuredData;

import java.util.List;
import java.util.Map;

public record AgentEvaluationExecution(
        AgentSkillCode actualSkill,
        Map<String, String> actualArguments,
        String answer,
        AgentStructuredData data,
        AgentPagination pagination,
        List<AgentAction> actions,
        List<AgentEvaluationCitation> citations) {
}
