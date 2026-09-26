package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record KnowledgeChatMessageVO(Long id, Long sessionId, String role, String content, String citationsJson, LocalDateTime createdAt) {
}
