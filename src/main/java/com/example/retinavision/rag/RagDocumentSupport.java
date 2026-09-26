package com.example.retinavision.rag;

import org.springframework.ai.document.Document;

public final class RagDocumentSupport {
    private RagDocumentSupport() {
    }

    public static Document toDocument(QdrantSearchHit hit) {
        return Document.builder()
                .id(hit.pointId())
                .text(hit.text())
                .score(hit.score())
                .metadata("documentId", hit.documentId())
                .metadata("chunkId", hit.chunkId())
                .metadata("documentTitle", hit.documentTitle())
                .metadata("source", hit.source())
                .metadata("score", hit.score())
                .build();
    }

    public static QdrantSearchHit toHit(Document document) {
        return new QdrantSearchHit(
                document.getId(), number(document, "score").doubleValue(),
                number(document, "documentId").longValue(), number(document, "chunkId").longValue(),
                string(document, "documentTitle"), string(document, "source"), document.getText());
    }

    private static Number number(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalArgumentException("RAG document metadata is missing numeric field: " + key);
    }

    private static String string(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : value.toString();
    }
}
