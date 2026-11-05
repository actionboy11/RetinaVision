package com.example.retinavision.pojo.knowledge.vo;

import java.time.LocalDateTime;

public record KnowledgeChatMessageVO(Long id, Long sessionId, String role, String content, String citationsJson, LocalDateTime createdAt) {
}
