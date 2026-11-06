package com.example.retinavision.service.governance.evaluation;

import com.example.retinavision.pojo.governance.evaluation.AgentEvaluationCaseOutcome;

import com.example.retinavision.pojo.governance.entity.AgentEvaluationCaseEntity;
import com.example.retinavision.pojo.governance.entity.AgentEvaluationRunEntity;

@FunctionalInterface
public interface AgentEvaluationCaseExecutor {
    AgentEvaluationCaseOutcome execute(AgentEvaluationRunEntity run, AgentEvaluationCaseEntity testCase);
}
