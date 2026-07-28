package com.example.retinavision.analysis.infrastructure.persistence;

import com.example.retinavision.analysis.application.model.InferenceOutput;
import com.example.retinavision.analysis.application.port.out.AnalysisResultStore;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;

public final class MyBatisAnalysisResultStore implements AnalysisResultStore {

    private final AnalysisResultMapper resultMapper;
    private final ImageMapper imageMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MyBatisAnalysisResultStore(
            AnalysisResultMapper resultMapper,
            ImageMapper imageMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.resultMapper = resultMapper;
        this.imageMapper = imageMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public long saveQuality(long taskId, long imageFileId, InferenceOutput output) {
        LocalDateTime now = LocalDateTime.now(clock);
        AnalysisResultEntity result = newResult(
                taskId, TaskType.IMAGE_QUALITY_CHECK, output, now);
        resultMapper.insert(result);

        ImageFileEntity image = imageMapper.selectById(imageFileId);
        if (image != null && Long.valueOf(taskId).equals(image.getQualityTaskId())) {
            image.setQualityStatus(ImageQualityStatus.valueOf(
                    String.valueOf(output.resultJson().get("grade"))));
            Object score = output.resultJson().get("score");
            image.setQualityScore(score instanceof Number number
                    ? number.doubleValue()
                    : null);
            image.setQualityResultId(result.getId());
            image.setQualityCheckedAt(now);
            image.setUpdatedAt(now);
            imageMapper.updateById(image);
        }
        return result.getId();
    }

    @Override
    public long saveSegmentation(
            long taskId,
            InferenceOutput output,
            String maskObjectKey) {
        LocalDateTime now = LocalDateTime.now(clock);
        AnalysisResultEntity result = newResult(
                taskId, TaskType.VESSEL_SEGMENTATION, output, now);
        result.setMaskBucket("local");
        result.setMaskObjectKey(maskObjectKey);
        resultMapper.insert(result);
        return result.getId();
    }

    private AnalysisResultEntity newResult(
            long taskId,
            TaskType type,
            InferenceOutput output,
            LocalDateTime now) {
        try {
            return AnalysisResultEntity.builder()
                    .taskId(taskId)
                    .resultType(type)
                    .resultJson(objectMapper.writeValueAsString(output.resultJson()))
                    .modelName(output.modelName())
                    .modelVersion(output.modelVersion())
                    .processingTimeMs(output.processingTimeMs())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化分析结果", exception);
        }
    }
}
