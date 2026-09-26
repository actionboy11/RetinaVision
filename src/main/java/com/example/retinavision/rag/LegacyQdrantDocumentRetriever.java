package com.example.retinavision.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "retina.ai", name = "framework", havingValue = "legacy", matchIfMissing = true)
public class LegacyQdrantDocumentRetriever implements KnowledgeDocumentRetriever {
    private final EmbeddingClient embeddings;
    private final QdrantClient qdrant;
    private final QdrantProperties properties;
    private final int topK;

    public LegacyQdrantDocumentRetriever(EmbeddingClient embeddings,
                                         QdrantClient qdrant,
                                         QdrantProperties properties,
                                         @Value("${retina.qdrant.top-k:5}") int topK) {
        this.embeddings = embeddings;
        this.qdrant = qdrant;
        this.properties = properties;
        this.topK = topK;
    }

    @Override
    public List<Document> retrieve(Query query) {
        return retrieve(properties.getCollection(), query, topK);
    }

    @Override
    public List<Document> retrieve(String collection, Query query, int limit) {
        return qdrant.search(collection, embeddings.embed(query.text()), limit).stream()
                .map(RagDocumentSupport::toDocument)
                .toList();
    }
}
