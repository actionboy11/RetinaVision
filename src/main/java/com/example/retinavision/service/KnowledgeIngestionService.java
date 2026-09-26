package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.KnowledgeDocumentCreateDTO;
import com.example.retinavision.pojo.Entity.KnowledgeDocumentEntity;

import java.util.List;

public interface KnowledgeIngestionService {
    KnowledgeDocumentEntity create(KnowledgeDocumentCreateDTO request, Integer userId);
    List<KnowledgeDocumentEntity> list();
    KnowledgeDocumentEntity reindex(Long documentId);
    KnowledgeDocumentEntity updateStatus(Long documentId, String status);
    void delete(Long documentId);
}
