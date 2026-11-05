package com.example.retinavision.pojo.agent.vo;

import java.time.LocalDateTime;

public record AgentChatSessionVO(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
