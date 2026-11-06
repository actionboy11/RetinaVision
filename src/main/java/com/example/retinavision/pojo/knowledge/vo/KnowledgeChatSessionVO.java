package com.example.retinavision.pojo.knowledge.vo;

import java.time.LocalDateTime;

public record KnowledgeChatSessionVO(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
