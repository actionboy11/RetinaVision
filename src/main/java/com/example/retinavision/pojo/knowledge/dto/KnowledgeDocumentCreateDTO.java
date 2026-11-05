package com.example.retinavision.pojo.knowledge.dto;

import com.example.retinavision.enumeration.knowledge.KnowledgeAudience;

public record KnowledgeDocumentCreateDTO(String title, String source, String category,
                                         KnowledgeAudience audience, String content) {
    public KnowledgeDocumentCreateDTO(String title, String source, String category, String content) {
        this(title, source, category, KnowledgeAudience.PUBLIC, content);
    }
}
