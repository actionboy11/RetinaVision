package com.example.retinavision.pojo.VO;

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
