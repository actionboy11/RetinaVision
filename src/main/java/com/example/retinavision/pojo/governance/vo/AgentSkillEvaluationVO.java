package com.example.retinavision.pojo.governance.vo;

import java.time.LocalDateTime;

public record AgentSkillEvaluationVO(Long id, String status, int totalCount,
                                     double routingAccuracy, double parameterAccuracy,
                                     boolean safetyPassed, String failureSamplesJson,
                                     LocalDateTime completedAt) {
}
