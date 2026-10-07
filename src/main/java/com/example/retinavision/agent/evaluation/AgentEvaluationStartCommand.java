package com.example.retinavision.agent.evaluation;

import java.util.Map;

public record AgentEvaluationStartCommand(
        Long datasetId,
        String targetRole,
        String modelKey,
        Map<String, Long> skillVersions,
        Map<String, Long> promptVersions,
        Integer createdBy) {
}
