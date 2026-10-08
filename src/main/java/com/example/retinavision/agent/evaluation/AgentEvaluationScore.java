package com.example.retinavision.agent.evaluation;

public record AgentEvaluationScore(
        double routingAccuracy,
        double parameterAccuracy,
        double queryAccuracy,
        double structurePassRate,
        double safetyPassRate,
        Double citationPassRate,
        long averageLatencyMs,
        long p95LatencyMs,
        boolean automatedPass,
        boolean invalid) {
}
