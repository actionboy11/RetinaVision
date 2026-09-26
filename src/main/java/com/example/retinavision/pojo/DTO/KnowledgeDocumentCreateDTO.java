package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.KnowledgeAudience;

public record KnowledgeDocumentCreateDTO(String title, String source, String category,
                                         KnowledgeAudience audience, String content) {
    public KnowledgeDocumentCreateDTO(String title, String source, String category, String content) {
        this(title, source, category, KnowledgeAudience.PUBLIC, content);
    }
}
