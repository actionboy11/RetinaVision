package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record PromptTemplateVO(
        String templateCode,
        String name,
        String scenario,
        String description,
        String status,
        Integer activeVersion,
        LocalDateTime updatedAt) {
}
