package com.example.retinavision.pojo.governance.vo;

import java.time.LocalDateTime;

public record AgentSkillVersionVO(Long id, Integer version, String routingExamplesJson,
                                  String workflowPrompt, String answerStyle, boolean active,
                                  LocalDateTime createdAt) {
}
