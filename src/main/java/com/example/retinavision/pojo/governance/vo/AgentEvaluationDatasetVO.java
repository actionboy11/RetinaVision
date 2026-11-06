package com.example.retinavision.pojo.governance.vo;

public record AgentEvaluationDatasetVO(Long id, String datasetCode, String name,
                                       String targetRole, Integer version, String status,
                                       Integer sampleCount, String description) {
}
