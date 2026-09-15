package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record KnowledgeDocumentVO(
        Long id,
        String title,
        String source,
        String category,
        String status,
        Integer version,
        Integer chunkCount,
        String failureReason,
        LocalDateTime lastIndexedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
