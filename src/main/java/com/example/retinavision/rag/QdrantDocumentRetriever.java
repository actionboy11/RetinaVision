package com.example.retinavision.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "retina.ai", name = "framework", havingValue = "spring-ai")
public class QdrantDocumentRetriever implements KnowledgeDocumentRetriever {
    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrant;
    private final int topK;

    public QdrantDocumentRetriever(EmbeddingModel embeddingModel,
                                   QdrantClient qdrant,
                                   @Value("${retina.qdrant.top-k:5}") int topK) {
        this.embeddingModel = embeddingModel;
        this.qdrant = qdrant;
        this.topK = topK;
    }

    @Override
    public List<Document> retrieve(Query query) {
        return qdrant.search(embeddingModel.embed(query.text()), topK).stream()
                .map(RagDocumentSupport::toDocument)
                .toList();
    }

    @Override
    public List<Document> retrieve(String collection, Query query, int limit) {
        return qdrant.search(collection, embeddingModel.embed(query.text()), limit).stream()
                .map(RagDocumentSupport::toDocument)
                .toList();
    }
}
