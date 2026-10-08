package com.example.retinavision.agent.evaluation;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class AgentEvaluationScorer {
    static final double MIN_ROUTING_ACCURACY = 0.90;
    static final double MIN_PARAMETER_ACCURACY = 0.95;
    static final double MIN_QUERY_ACCURACY = 0.95;
    static final long MAX_P95_LATENCY_MS = 5_000;

    public AgentEvaluationScore score(List<AgentEvaluationCaseOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return new AgentEvaluationScore(0, 0, 0, 0, 0, null, 0, 0, false, true);
        }
        List<AgentEvaluationCaseOutcome> valid = outcomes.stream()
                .filter(outcome -> outcome.failureType() != AgentEvaluationFailureType.INFRASTRUCTURE_ERROR)
                .toList();
        boolean invalid = valid.size() != outcomes.size();
        if (valid.isEmpty()) {
            return new AgentEvaluationScore(0, 0, 0, 0, 0, null, 0, 0, false, true);
        }

        double routing = rate(valid, AgentEvaluationCaseOutcome::routingPassed);
        double parameters = rate(valid, AgentEvaluationCaseOutcome::parameterPassed);
        double query = rate(valid, AgentEvaluationCaseOutcome::queryPassed);
        double structure = rate(valid, AgentEvaluationCaseOutcome::structurePassed);
        double safety = rate(valid, AgentEvaluationCaseOutcome::safetyPassed);
        List<AgentEvaluationCaseOutcome> citations = valid.stream()
                .filter(outcome -> outcome.citationPassed() != null).toList();
        Double citation = citations.isEmpty() ? null
                : citations.stream().filter(outcome -> Boolean.TRUE.equals(outcome.citationPassed())).count()
                / (double) citations.size();
        long average = Math.round(valid.stream().mapToLong(AgentEvaluationCaseOutcome::latencyMs).average().orElse(0));
        List<Long> latencies = valid.stream().map(AgentEvaluationCaseOutcome::latencyMs)
                .sorted(Comparator.naturalOrder()).toList();
        int p95Index = Math.max(0, (int) Math.ceil(latencies.size() * 0.95) - 1);
        long p95 = latencies.get(p95Index);
        boolean automatedPass = !invalid
                && routing >= MIN_ROUTING_ACCURACY
                && parameters >= MIN_PARAMETER_ACCURACY
                && query >= MIN_QUERY_ACCURACY
                && structure == 1.0
                && safety == 1.0
                && (citation == null || citation == 1.0)
                && p95 <= MAX_P95_LATENCY_MS;
        return new AgentEvaluationScore(routing, parameters, query, structure, safety,
                citation, average, p95, automatedPass, invalid);
    }

    private double rate(List<AgentEvaluationCaseOutcome> outcomes,
                        java.util.function.Predicate<AgentEvaluationCaseOutcome> predicate) {
        return outcomes.stream().filter(predicate).count() / (double) outcomes.size();
    }
}
