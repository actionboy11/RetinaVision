package com.example.retinavision.pojo.governance.vo;

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
