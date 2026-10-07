package com.example.retinavision.rag;

import com.example.retinavision.enumeration.KnowledgeAudience;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.mapper.KnowledgeDocumentMapper;
import com.example.retinavision.pojo.Entity.KnowledgeDocumentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeAudiencePolicyTest {
    @Test
    void patientReceivesOnlyActivePublicAndPatientDocuments() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        when(mapper.selectById(1L)).thenReturn(document(1L, KnowledgeAudience.PUBLIC, "ACTIVE"));
        when(mapper.selectById(2L)).thenReturn(document(2L, KnowledgeAudience.PATIENT, "ACTIVE"));
        when(mapper.selectById(3L)).thenReturn(document(3L, KnowledgeAudience.CLINICAL, "ACTIVE"));
        when(mapper.selectById(4L)).thenReturn(document(4L, KnowledgeAudience.RESEARCH, "ACTIVE"));
        when(mapper.selectById(5L)).thenReturn(document(5L, KnowledgeAudience.ADMIN, "ACTIVE"));
        when(mapper.selectById(6L)).thenReturn(document(6L, KnowledgeAudience.PUBLIC, "DISABLED"));
        KnowledgeAudiencePolicy policy = new KnowledgeAudiencePolicy(mapper);

        List<Document> visible = policy.filter(List.of(
                chunk(1L), chunk(2L), chunk(3L), chunk(4L), chunk(5L), chunk(6L)), UserRole.USER);

        assertThat(visible).extracting(item -> item.getMetadata().get("documentId"))
                .containsExactly(1L, 2L);
    }

    private KnowledgeDocumentEntity document(long id, KnowledgeAudience audience, String status) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(id);
        entity.setAudience(audience);
        entity.setStatus(status);
        return entity;
    }

    private Document chunk(long documentId) {
        return Document.builder().id("p" + documentId).text("knowledge")
                .metadata("documentId", documentId).build();
    }
}
