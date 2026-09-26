package com.example.retinavision.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QdrantDocumentRetrieverTest {

    @Test
    void convertsQdrantHitsToDocumentsWithoutLosingCitationMetadata() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrant = mock(QdrantClient.class);
        float[] vector = new float[]{0.1f, 0.2f};
        when(embeddingModel.embed("视网膜血管分割有什么作用")).thenReturn(vector);
        when(qdrant.search(vector, 5)).thenReturn(List.of(new QdrantSearchHit(
                "point-1", 0.91, 7L, 11L, "血管分割基础", "院内知识库", "分割用于量化血管结构"
        )));

        QdrantDocumentRetriever retriever = new QdrantDocumentRetriever(embeddingModel, qdrant, 5);

        List<Document> documents = retriever.retrieve(new Query("视网膜血管分割有什么作用"));

        assertThat(documents).hasSize(1);
        Document document = documents.get(0);
        assertThat(document.getId()).isEqualTo("point-1");
        assertThat(document.getText()).isEqualTo("分割用于量化血管结构");
        assertThat(document.getScore()).isEqualTo(0.91);
        assertThat(document.getMetadata()).containsEntry("documentId", 7L)
                .containsEntry("chunkId", 11L)
                .containsEntry("documentTitle", "血管分割基础")
                .containsEntry("source", "院内知识库")
                .containsEntry("score", 0.91);
        verify(qdrant).search(vector, 5);
    }
}
