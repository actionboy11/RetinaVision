package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record AgentChatMessageVO(Long id, Long sessionId, String role, String content,
                                 String structuredContentJson, LocalDateTime createdAt) {
}
