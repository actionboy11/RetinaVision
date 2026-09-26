package com.example.retinavision.rag;

import com.example.retinavision.enumeration.KnowledgeAudience;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.mapper.KnowledgeDocumentMapper;
import com.example.retinavision.pojo.Entity.KnowledgeDocumentEntity;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeAudiencePolicy {
    private final KnowledgeDocumentMapper documents;

    public KnowledgeAudiencePolicy(KnowledgeDocumentMapper documents) {
        this.documents = documents;
    }

    public List<Document> filter(List<Document> chunks, UserRole role) {
        if (chunks == null || role == null) return List.of();
        return chunks.stream().filter(chunk -> visible(document(chunk), role)).toList();
    }

    private KnowledgeDocumentEntity document(Document chunk) {
        Object raw = chunk.getMetadata().get("documentId");
        Long id = raw instanceof Number number ? number.longValue() : null;
        return id == null ? null : documents.selectById(id);
    }

    private boolean visible(KnowledgeDocumentEntity document, UserRole role) {
        if (document == null || !"ACTIVE".equals(document.getStatus())) return false;
        KnowledgeAudience audience = document.getAudience() == null ? KnowledgeAudience.PUBLIC : document.getAudience();
        return switch (audience) {
            case PUBLIC -> true;
            case PATIENT -> role == UserRole.USER || role == UserRole.DOCTOR || role == UserRole.ADMIN;
            case CLINICAL -> role == UserRole.DOCTOR || role == UserRole.ADMIN;
            case RESEARCH -> role == UserRole.RESEARCHER || role == UserRole.ADMIN;
            case ADMIN -> role == UserRole.ADMIN;
        };
    }
}
