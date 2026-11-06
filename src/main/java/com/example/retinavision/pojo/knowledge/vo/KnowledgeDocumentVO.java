package com.example.retinavision.pojo.knowledge.vo;

import java.time.LocalDateTime;
import com.example.retinavision.enumeration.knowledge.KnowledgeAudience;

public record KnowledgeDocumentVO(
        Long id,
        String title,
        String source,
        String category,
        KnowledgeAudience audience,
        String status,
        Integer version,
        Integer chunkCount,
        String failureReason,
        LocalDateTime lastIndexedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
