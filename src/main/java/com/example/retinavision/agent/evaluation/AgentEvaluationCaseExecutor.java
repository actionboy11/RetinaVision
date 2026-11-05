package com.example.retinavision.agent.evaluation;

import com.example.retinavision.pojo.governance.entity.AgentEvaluationCaseEntity;
import com.example.retinavision.pojo.governance.entity.AgentEvaluationRunEntity;

@FunctionalInterface
public interface AgentEvaluationCaseExecutor {
    AgentEvaluationCaseOutcome execute(AgentEvaluationRunEntity run, AgentEvaluationCaseEntity testCase);
}
