package com.example.retinavision.rag.knowledge;

public record QdrantSearchHit(
        String pointId,
        double score,
        Long documentId,
        Long chunkId,
        String documentTitle,
        String source,
        String text
) {
}
