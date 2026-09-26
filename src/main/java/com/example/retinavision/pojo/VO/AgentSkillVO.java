package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record AgentSkillVO(String skillCode, String name, String description, String status,
                           Long activeVersionId, LocalDateTime updatedAt) {
}
