package com.example.retinavision.pojo.analysis.domain;

import java.time.LocalDateTime;

public record TaskTransition(
        AnalysisTaskStatus from,
        AnalysisTaskStatus to,
        String message,
        LocalDateTime at) {
}
