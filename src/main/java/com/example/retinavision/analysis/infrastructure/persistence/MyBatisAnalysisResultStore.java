package com.example.retinavision.analysis.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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

        ImageQualityStatus qualityStatus = ImageQualityStatus.valueOf(
                String.valueOf(output.resultJson().get("grade")));
        Object score = output.resultJson().get("score");
        Double qualityScore = score instanceof Number number
                ? number.doubleValue()
                : null;
        imageMapper.update(null, new UpdateWrapper<ImageFileEntity>()
                .eq("id", imageFileId)
                .eq("quality_task_id", taskId)
                .set("quality_status", qualityStatus)
                .set("quality_score", qualityScore)
                .set("quality_result_id", result.getId())
                .set("quality_checked_at", now)
                .set("updated_at", now));
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
