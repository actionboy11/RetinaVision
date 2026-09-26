package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record AgentSkillExecutionVO(Long id, String skillCode, Integer skillVersion,
                                    Double confidence, Boolean success, Long latencyMs,
                                    String errorType, LocalDateTime createdAt) {
}
