package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;

public record LlmCallLogVO(
        Long id,
        String scenario,
        String templateCode,
        Integer templateVersion,
        String provider,
        String model,
        Boolean success,
        Long latencyMs,
        String errorSummary,
        LocalDateTime createdAt) {
}
