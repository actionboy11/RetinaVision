package com.example.retinavision.agent.evaluation;

import com.example.retinavision.pojo.Entity.AgentEvaluationCaseEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;

@FunctionalInterface
public interface AgentEvaluationCaseExecutor {
    AgentEvaluationCaseOutcome execute(AgentEvaluationRunEntity run, AgentEvaluationCaseEntity testCase);
}
