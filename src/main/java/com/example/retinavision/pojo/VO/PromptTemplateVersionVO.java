package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record PromptTemplateVersionVO(
        Long id,
        String templateCode,
        Integer version,
        String systemPrompt,
        String outputContract,
        String safetyPolicy,
        Boolean active,
        Integer createdBy,
        LocalDateTime createdAt) {
}
