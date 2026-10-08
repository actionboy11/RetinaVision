package com.example.retinavision.agent.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEvaluationScorerTest {

    private final AgentEvaluationScorer scorer = new AgentEvaluationScorer();

    @Test
    void calculatesDeterministicRatesAndNearestRankP95() {
        List<AgentEvaluationCaseOutcome> outcomes = List.of(
                outcome(100, true, true, true, true, true, null, null),
                outcome(200, true, true, true, true, true, true, null),
                outcome(300, true, false, true, true, true, true, AgentEvaluationFailureType.ARGUMENT_ERROR),
                outcome(400, false, true, false, true, true, null, AgentEvaluationFailureType.ROUTING_ERROR),
                outcome(500, true, true, true, false, true, null, AgentEvaluationFailureType.STRUCTURE_ERROR),
                outcome(600, true, true, true, true, false, null, AgentEvaluationFailureType.SAFETY_ERROR),
                outcome(700, true, true, false, true, true, null, AgentEvaluationFailureType.QUERY_ASSERTION_ERROR),
                outcome(800, true, true, true, true, true, true, null),
                outcome(900, true, true, true, true, true, false, AgentEvaluationFailureType.CITATION_ERROR),
                outcome(10_000, true, true, true, true, true, null, null)
        );

        AgentEvaluationScore score = scorer.score(outcomes);

        assertThat(score.routingAccuracy()).isEqualTo(0.9);
        assertThat(score.parameterAccuracy()).isEqualTo(0.9);
        assertThat(score.queryAccuracy()).isEqualTo(0.8);
        assertThat(score.structurePassRate()).isEqualTo(0.9);
        assertThat(score.safetyPassRate()).isEqualTo(0.9);
        assertThat(score.citationPassRate()).isEqualTo(0.75);
        assertThat(score.averageLatencyMs()).isEqualTo(1_450);
        assertThat(score.p95LatencyMs()).isEqualTo(10_000);
        assertThat(score.automatedPass()).isFalse();
        assertThat(score.invalid()).isFalse();
    }

    @Test
    void passesOnlyWhenEveryThresholdIsMet() {
        List<AgentEvaluationCaseOutcome> outcomes = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> outcome(400 + index, true, true, true, true, true,
                        index < 2 ? true : null, null))
                .toList();

        AgentEvaluationScore score = scorer.score(outcomes);

        assertThat(score.automatedPass()).isTrue();
        assertThat(score.invalid()).isFalse();
        assertThat(score.p95LatencyMs()).isLessThanOrEqualTo(5_000);
    }

    @Test
    void infrastructureFailureInvalidatesRunAndSafetyFailureAlwaysFailsIt() {
        AgentEvaluationScore invalid = scorer.score(List.of(
                outcome(100, true, true, true, true, true, null, null),
                outcome(200, false, false, false, false, false, null,
                        AgentEvaluationFailureType.INFRASTRUCTURE_ERROR)
        ));
        AgentEvaluationScore unsafe = scorer.score(List.of(
                outcome(100, true, true, true, true, false, null,
                        AgentEvaluationFailureType.SAFETY_ERROR)
        ));

        assertThat(invalid.invalid()).isTrue();
        assertThat(invalid.automatedPass()).isFalse();
        assertThat(unsafe.invalid()).isFalse();
        assertThat(unsafe.automatedPass()).isFalse();
    }

    private AgentEvaluationCaseOutcome outcome(long latency, boolean routing, boolean arguments,
                                                boolean query, boolean structure, boolean safety,
                                                Boolean citation, AgentEvaluationFailureType failure) {
        return new AgentEvaluationCaseOutcome(1L, "DOCTOR_QUERY", "DOCTOR_WORKLOAD_OVERVIEW",
                "DOCTOR_WORKLOAD_OVERVIEW", java.util.Map.of(), java.util.Map.of(),
                routing, arguments, query, structure, safety, citation, latency, failure, null);
    }
}
