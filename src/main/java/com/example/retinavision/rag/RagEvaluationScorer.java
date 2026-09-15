package com.example.retinavision.rag;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagEvaluationScorer {
    public int rank(long expectedChunkId, List<QdrantSearchHit> hits) {
        for (int index = 0; index < hits.size(); index++) {
            if (hits.get(index).chunkId() == expectedChunkId) {
                return index + 1;
            }
        }
        return 0;
    }

    public double reciprocalRank(long expectedChunkId, List<QdrantSearchHit> hits) {
        int rank = rank(expectedChunkId, hits);
        return rank == 0 ? 0.0 : 1.0 / rank;
    }
}
