package com.example.retinavision.pojo.governance.evaluation;

import com.example.retinavision.pojo.agent.AgentAction;
import com.example.retinavision.pojo.agent.AgentPagination;
import com.example.retinavision.pojo.agent.AgentSkillCode;
import com.example.retinavision.pojo.agent.AgentStructuredData;

import com.example.retinavision.pojo.agent.AgentAction;
import com.example.retinavision.pojo.agent.AgentPagination;
import com.example.retinavision.pojo.agent.AgentSkillCode;
import com.example.retinavision.pojo.agent.AgentStructuredData;

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
