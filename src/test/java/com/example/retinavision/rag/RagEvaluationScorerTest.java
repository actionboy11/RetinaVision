package com.example.retinavision.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagEvaluationScorerTest {
    private final RagEvaluationScorer scorer = new RagEvaluationScorer();

    @Test
    void separatesRetrievalHitAndRankFromGeneration() {
        List<QdrantSearchHit> hits = List.of(
                new QdrantSearchHit("a", 0.9, 1L, 11L, "A", "test", "A"),
                new QdrantSearchHit("b", 0.8, 2L, 22L, "B", "test", "B"));

        assertThat(scorer.rank(22L, hits)).isEqualTo(2);
        assertThat(scorer.rank(33L, hits)).isZero();
        assertThat(scorer.reciprocalRank(22L, hits)).isEqualTo(0.5);
        assertThat(scorer.reciprocalRank(33L, hits)).isZero();
    }
}
