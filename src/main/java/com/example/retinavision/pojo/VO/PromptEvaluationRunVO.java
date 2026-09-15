package com.example.retinavision.pojo.VO;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record PromptEvaluationRunVO(
        Long id,
        String templateCode,
        Long baselineVersionId,
        Long candidateVersionId,
        String sampleVersion,
        String provider,
        String model,
        String embeddingModel,
        Double scoreThreshold,
        String status,
        Boolean automatedPass,
        String doctorDecision,
        Integer doctorScore,
        String doctorNote,
        Integer reviewedBy,
        LocalDateTime reviewedAt,
        Integer createdBy,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        String failureReason,
        JsonNode result
) {}
