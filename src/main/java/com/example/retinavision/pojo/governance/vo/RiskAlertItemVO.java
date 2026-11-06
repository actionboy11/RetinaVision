package com.example.retinavision.pojo.governance.vo;
import com.example.retinavision.enumeration.analysis.TaskType;

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
