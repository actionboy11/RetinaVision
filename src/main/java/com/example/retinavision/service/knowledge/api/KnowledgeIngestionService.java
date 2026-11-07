package com.example.retinavision.service.knowledge.api;

import com.example.retinavision.pojo.knowledge.dto.KnowledgeDocumentCreateDTO;
import com.example.retinavision.pojo.knowledge.entity.KnowledgeDocumentEntity;

import java.util.List;

public interface KnowledgeIngestionService {
    KnowledgeDocumentEntity create(KnowledgeDocumentCreateDTO request, Integer userId);
    List<KnowledgeDocumentEntity> list();
    KnowledgeDocumentEntity reindex(Long documentId);
    KnowledgeDocumentEntity updateStatus(Long documentId, String status);
    void delete(Long documentId);
}
