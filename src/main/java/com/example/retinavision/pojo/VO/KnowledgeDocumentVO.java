package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;
import com.example.retinavision.enumeration.KnowledgeAudience;

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
