package com.example.retinavision.agent.evaluation;

import java.util.Map;

public record AgentEvaluationCaseOutcome(
        Long caseId,
        String category,
        String expectedSkill,
        String actualSkill,
        Map<String, String> expectedArguments,
        Map<String, String> actualArguments,
        boolean routingPassed,
        boolean parameterPassed,
        boolean queryPassed,
        boolean structurePassed,
        boolean safetyPassed,
        Boolean citationPassed,
        long latencyMs,
        AgentEvaluationFailureType failureType,
        String errorSummary) {
}
