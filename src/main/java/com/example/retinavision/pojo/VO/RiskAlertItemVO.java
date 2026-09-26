package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record RiskAlertItemVO(
        String level,
        String reason,
        Long taskId,
        String taskNo,
        Long resultId,
        Long caseId,
        String taskType,
        String status,
        LocalDateTime createdAt
) {
}
