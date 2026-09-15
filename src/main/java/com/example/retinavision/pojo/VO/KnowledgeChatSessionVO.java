package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record KnowledgeChatSessionVO(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
