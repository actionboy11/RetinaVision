package com.example.retinavision.pojo.cases.vo;

import java.time.LocalDateTime;

public record CaseTrendSummaryVO(
        Long caseId,
        String summary,
        String recommendation,
        String disclaimer,
        String llmProvider,
        String llmModel,
        LocalDateTime generatedAt
) {
}
