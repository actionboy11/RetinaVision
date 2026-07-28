package com.example.retinavision.analysis.infrastructure.persistence;

import com.example.retinavision.analysis.application.model.InferenceOutput;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.enumeration.ImageStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisAnalysisResultStoreTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T06:07:08Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 6, 7, 8);

    private final AnalysisResultMapper resultMapper = mock(AnalysisResultMapper.class);
    private final ImageMapper imageMapper = mock(ImageMapper.class);
    private MyBatisAnalysisResultStore store;

    @BeforeEach
    void setUp() {
        store = new MyBatisAnalysisResultStore(
                resultMapper, imageMapper, new ObjectMapper(), CLOCK);
        doAnswer(invocation -> {
            AnalysisResultEntity result = invocation.getArgument(0);
            result.setId(501L);
            return 1;
        }).when(resultMapper).insert(any(AnalysisResultEntity.class));
    }

    @Test
    void savesQualityResultAndProjectsItOnlyForCurrentQualityTask() throws Exception {
        ImageFileEntity image = ImageFileEntity.builder()
                .id(20L)
                .status(ImageStatus.UPLOADED)
                .qualityTaskId(100L)
                .qualityStatus(ImageQualityStatus.CHECKING)
                .build();
        when(imageMapper.selectById(20L)).thenReturn(image);
        InferenceOutput output = new InferenceOutput(
                Map.of("grade", "PASS", "score", 88.5),
                "quality-model", "v1", 7, null);

        long resultId = store.saveQuality(100L, 20L, output);

        assertThat(resultId).isEqualTo(501L);
        ArgumentCaptor<AnalysisResultEntity> resultCaptor =
                ArgumentCaptor.forClass(AnalysisResultEntity.class);
        verify(resultMapper).insert(resultCaptor.capture());
        AnalysisResultEntity result = resultCaptor.getValue();
        assertThat(result.getTaskId()).isEqualTo(100L);
        assertThat(result.getResultType()).isEqualTo(TaskType.IMAGE_QUALITY_CHECK);
        assertThat(new ObjectMapper().readTree(result.getResultJson()).get("score").asDouble())
                .isEqualTo(88.5);
        assertThat(result.getModelName()).isEqualTo("quality-model");
        assertThat(result.getModelVersion()).isEqualTo("v1");
        assertThat(result.getProcessingTimeMs()).isEqualTo(7);
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        assertThat(image.getQualityStatus()).isEqualTo(ImageQualityStatus.PASS);
        assertThat(image.getQualityScore()).isEqualTo(88.5);
        assertThat(image.getQualityResultId()).isEqualTo(501L);
        assertThat(image.getQualityCheckedAt()).isEqualTo(NOW);
        assertThat(image.getUpdatedAt()).isEqualTo(NOW);
        verify(imageMapper).updateById(image);
    }

    @Test
    void supersededQualityTaskKeepsHistoricalResultWithoutOverwritingProjection() {
        ImageFileEntity image = ImageFileEntity.builder()
                .id(20L)
                .qualityTaskId(101L)
                .qualityStatus(ImageQualityStatus.CHECKING)
                .build();
        when(imageMapper.selectById(20L)).thenReturn(image);

        long resultId = store.saveQuality(100L, 20L, new InferenceOutput(
                Map.of("grade", "FAIL", "score", 20),
                "quality-model", "v1", 7, null));

        assertThat(resultId).isEqualTo(501L);
        verify(resultMapper).insert(any(AnalysisResultEntity.class));
        verify(imageMapper, never()).updateById(any(ImageFileEntity.class));
        assertThat(image.getQualityStatus()).isEqualTo(ImageQualityStatus.CHECKING);
        assertThat(image.getQualityResultId()).isNull();
    }

    @Test
    void savesSegmentationMetadataAndLocalMaskObjectKey() throws Exception {
        InferenceOutput output = new InferenceOutput(
                Map.of("vesselAreaRatio", 0.25),
                "FSCNet", "v2", 123, "/v1/artifacts/mask");

        long resultId = store.saveSegmentation(
                200L, output, "tasks/200/mask.png");

        assertThat(resultId).isEqualTo(501L);
        ArgumentCaptor<AnalysisResultEntity> captor =
                ArgumentCaptor.forClass(AnalysisResultEntity.class);
        verify(resultMapper).insert(captor.capture());
        AnalysisResultEntity result = captor.getValue();
        assertThat(result.getTaskId()).isEqualTo(200L);
        assertThat(result.getResultType()).isEqualTo(TaskType.VESSEL_SEGMENTATION);
        assertThat(result.getMaskBucket()).isEqualTo("local");
        assertThat(result.getMaskObjectKey()).isEqualTo("tasks/200/mask.png");
        assertThat(result.getModelName()).isEqualTo("FSCNet");
        assertThat(result.getModelVersion()).isEqualTo("v2");
        assertThat(result.getProcessingTimeMs()).isEqualTo(123);
        assertThat(new ObjectMapper().readTree(result.getResultJson())
                .get("vesselAreaRatio").asDouble()).isEqualTo(0.25);
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
    }
}
