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
    void patientCannotReceiveClinicalDocument() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        when(mapper.selectById(1L)).thenReturn(document(1L, KnowledgeAudience.CLINICAL));
        when(mapper.selectById(2L)).thenReturn(document(2L, KnowledgeAudience.PATIENT));
        KnowledgeAudiencePolicy policy = new KnowledgeAudiencePolicy(mapper);

        List<Document> visible = policy.filter(List.of(chunk(1L), chunk(2L)), UserRole.USER);

        assertThat(visible).extracting(item -> item.getMetadata().get("documentId"))
                .containsExactly(2L);
    }

    private KnowledgeDocumentEntity document(long id, KnowledgeAudience audience) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(id);
        entity.setAudience(audience);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private Document chunk(long documentId) {
        return Document.builder().id("p" + documentId).text("knowledge")
                .metadata("documentId", documentId).build();
    }
}
