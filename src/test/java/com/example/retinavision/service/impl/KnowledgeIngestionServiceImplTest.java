package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.KnowledgeChunkMapper;
import com.example.retinavision.mapper.KnowledgeDocumentMapper;
import com.example.retinavision.pojo.DTO.KnowledgeDocumentCreateDTO;
import com.example.retinavision.pojo.Entity.KnowledgeChunkEntity;
import com.example.retinavision.pojo.Entity.KnowledgeDocumentEntity;
import com.example.retinavision.rag.EmbeddingClient;
import com.example.retinavision.rag.EmbeddingProperties;
import com.example.retinavision.rag.QdrantClient;
import com.example.retinavision.rag.QdrantPoint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIngestionServiceImplTest {
    private final KnowledgeDocumentMapper documents = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final EmbeddingProperties embeddingProperties = new EmbeddingProperties();
    private final QdrantClient qdrant = mock(QdrantClient.class);

    @Test
    void createStoresOriginalContentIndexesChunksAndMarksActive() {
        assignIds();
        embeddingProperties.setDimension(4);
        when(embeddings.embed(any())).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});
        KnowledgeIngestionServiceImpl service = new KnowledgeIngestionServiceImpl(documents, chunks, embeddings, embeddingProperties, qdrant);

        KnowledgeDocumentEntity document = service.create(new KnowledgeDocumentCreateDTO(
                "血管知识",
                "院内知识库",
                "MEDICAL_BASE",
                "# 血管知识\n\n视网膜血管分割用于辅助理解眼底结构。"), 7);

        assertThat(document.getStatus()).isEqualTo("ACTIVE");
        assertThat(document.getOriginalContent()).contains("视网膜血管分割");
        assertThat(document.getCategory()).isEqualTo("MEDICAL_BASE");
        assertThat(document.getChunkCount()).isEqualTo(1);
        assertThat(document.getLastIndexedAt()).isNotNull();
        verify(qdrant).ensureCollection(4);
        ArgumentCaptor<List<QdrantPoint>> points = ArgumentCaptor.forClass(List.class);
        verify(qdrant).upsert(points.capture());
        assertThat(points.getValue()).hasSize(1);
        assertThat(points.getValue().get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void reindexDeletesOldPointsAndChunksThenRebuildsFromOriginalContent() {
        assignIds();
        embeddingProperties.setDimension(4);
        when(embeddings.embed(any())).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});
        KnowledgeDocumentEntity existing = document(10L, "旧标题", "ACTIVE", "FAQ", "原始内容第一段\n\n原始内容第二段");
        when(documents.selectById(10L)).thenReturn(existing);
        when(chunks.selectList(any())).thenReturn(List.of(chunk(1L, "old-point-1"), chunk(2L, "old-point-2")));
        KnowledgeIngestionServiceImpl service = new KnowledgeIngestionServiceImpl(documents, chunks, embeddings, embeddingProperties, qdrant);

        KnowledgeDocumentEntity reindexed = service.reindex(10L);

        assertThat(reindexed.getStatus()).isEqualTo("ACTIVE");
        assertThat(reindexed.getChunkCount()).isGreaterThanOrEqualTo(1);
        verify(qdrant).deletePoints(List.of("old-point-1", "old-point-2"));
        verify(chunks).delete(any(Wrapper.class));
        verify(qdrant).upsert(any());
    }

    @Test
    void failedIndexingMarksDocumentFailedAndKeepsReason() {
        assignIds();
        when(embeddings.embed(any())).thenThrow(new BaseException(50000, "Embedding unavailable"));
        KnowledgeIngestionServiceImpl service = new KnowledgeIngestionServiceImpl(documents, chunks, embeddings, embeddingProperties, qdrant);

        assertThatThrownBy(() -> service.create(new KnowledgeDocumentCreateDTO("标题", "来源", "FAQ", "正文内容"), 7))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("Embedding unavailable");

        ArgumentCaptor<KnowledgeDocumentEntity> updated = ArgumentCaptor.forClass(KnowledgeDocumentEntity.class);
        verify(documents, org.mockito.Mockito.atLeastOnce()).updateById(updated.capture());
        assertThat(updated.getAllValues()).anySatisfy(document -> {
            assertThat(document.getStatus()).isEqualTo("FAILED");
            assertThat(document.getFailureReason()).contains("Embedding unavailable");
        });
    }

    @Test
    void sensitiveContentIsRejectedBeforeIndexing() {
        KnowledgeIngestionServiceImpl service = new KnowledgeIngestionServiceImpl(documents, chunks, embeddings, embeddingProperties, qdrant);

        assertThatThrownBy(() -> service.create(new KnowledgeDocumentCreateDTO(
                "敏感资料", "来源", "FAQ", "API Key: sk-ws-abcdefg1234567890"), 7))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("敏感信息");
    }

    private void assignIds() {
        AtomicLong documentId = new AtomicLong(1);
        AtomicLong chunkId = new AtomicLong(100);
        doAnswer(invocation -> {
            KnowledgeDocumentEntity document = invocation.getArgument(0);
            if (document.getId() == null) {
                document.setId(documentId.getAndIncrement());
            }
            return 1;
        }).when(documents).insert(any(KnowledgeDocumentEntity.class));
        doAnswer(invocation -> {
            KnowledgeChunkEntity chunk = invocation.getArgument(0);
            if (chunk.getId() == null) {
                chunk.setId(chunkId.getAndIncrement());
            }
            return 1;
        }).when(chunks).insert(any(KnowledgeChunkEntity.class));
    }

    private KnowledgeDocumentEntity document(Long id, String title, String status, String category, String originalContent) {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(id);
        document.setTitle(title);
        document.setSource("来源");
        document.setContentType("markdown");
        document.setStatus(status);
        document.setCategory(category);
        document.setOriginalContent(originalContent);
        document.setVersion(1);
        document.setUploadedBy(7);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        return document;
    }

    private KnowledgeChunkEntity chunk(Long id, String pointId) {
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(id);
        chunk.setDocumentId(10L);
        chunk.setChunkIndex(id.intValue());
        chunk.setChunkText("旧内容");
        chunk.setCharLength(3);
        chunk.setQdrantPointId(pointId);
        chunk.setCreatedAt(LocalDateTime.now());
        return chunk;
    }
}
