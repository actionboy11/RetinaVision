package com.example.retinavision.pojo.VO;

import java.time.LocalDateTime;
import java.util.List;

public record AgentEvaluationRunVO(Long id, Long datasetId, Integer datasetVersion,
                                   String targetRole, String modelKey, String provider, String model,
                                   String status, Progress progress, Metrics metrics,
                                   Boolean automatedPass, String reviewDecision, String reviewNote,
                                   List<Binding> bindings, LocalDateTime createdAt,
                                   LocalDateTime completedAt) {
    public record Progress(Integer completed, Integer total) {}
    public record Metrics(Double routingAccuracy, Double parameterAccuracy, Double queryAccuracy,
                          Double structurePassRate, Double safetyPassRate, Double citationPassRate,
                          Long averageLatencyMs, Long p95LatencyMs) {}
    public record Binding(String type, String code, Long versionId, String versionLabel) {}
}
