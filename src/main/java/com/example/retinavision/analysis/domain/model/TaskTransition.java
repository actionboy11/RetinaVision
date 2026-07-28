package com.example.retinavision.analysis.domain.model;

import java.time.LocalDateTime;

public record TaskTransition(
        AnalysisTaskStatus from,
        AnalysisTaskStatus to,
        String message,
        LocalDateTime at) {
}
