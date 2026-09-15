package com.example.retinavision.rag;

public record QdrantPoint(
        String id,
        float[] vector,
        Long documentId,
        Long chunkId,
        String documentTitle,
        String source,
        String category,
        String status,
        String text
) {
}
