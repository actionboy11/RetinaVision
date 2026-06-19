package com.example.retinavision.service.impl;

import com.example.retinavision.ai.AiInferenceClient;
import com.example.retinavision.ai.AiInferenceException;
import com.example.retinavision.ai.dto.AiInferenceResponse;
import com.example.retinavision.enumeration.ImageStatus;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mq.AnalysisTaskMessage;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.LogEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.service.AnalysisTaskExecutionService.ExecutionDisposition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisTaskExecutionServiceImplTest {
    @Mock private TaskMapper taskMapper;
    @Mock private ImageMapper imageMapper;
    @Mock private AnalysisResultMapper analysisResultMapper;
    @Mock private LogMapper logMapper;
    @Mock private AiInferenceClient aiInferenceClient;
    @TempDir Path tempDir;

    private Path imageRoot;
    private Path resultRoot;
    private AnalysisTaskExecutionServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        imageRoot = Files.createDirectories(tempDir.resolve("images"));
        resultRoot = Files.createDirectories(tempDir.resolve("results"));
        service = new AnalysisTaskExecutionServiceImpl(
                taskMapper,
                imageMapper,
                analysisResultMapper,
                logMapper,
                aiInferenceClient,
                new ObjectMapper(),
                imageRoot.toString(),
                resultRoot.toString()
        );
    }

    @Test
    void processPersistsResultMaskAndSuccessState() throws Exception {
        TaskEntity task = task(100L, TaskType.VESSEL_SEGMENTATION);
        Path image = imageRoot.resolve("cases/10/source.png");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[]{1, 2, 3});
        ImageFileEntity imageEntity = ImageFileEntity.builder()
                .id(20L)
                .status(ImageStatus.UPLOADED)
                .originalFilename("source.png")
                .fileType("image/png")
                .storageObjectKey("cases/10/source.png")
                .build();
        AiInferenceResponse response = inferenceResponse();
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(taskMapper.claimForExecution(org.mockito.ArgumentMatchers.eq(100L), any())).thenReturn(1);
        when(imageMapper.selectById(20L)).thenReturn(imageEntity);
        when(aiInferenceClient.segment(image, "source.png", "image/png")).thenReturn(response);
        when(aiInferenceClient.downloadMask(response.getMaskUrl())).thenReturn(new byte[]{9, 8, 7});

        ExecutionDisposition disposition = service.process(message(100L, TaskType.VESSEL_SEGMENTATION));

        assertThat(disposition).isEqualTo(ExecutionDisposition.SUCCESS);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(Files.readAllBytes(resultRoot.resolve("tasks/100/mask.png")))
                .containsExactly(9, 8, 7);
        ArgumentCaptor<AnalysisResultEntity> resultCaptor = ArgumentCaptor.forClass(AnalysisResultEntity.class);
        verify(analysisResultMapper).insert(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getTaskId()).isEqualTo(100L);
        assertThat(resultCaptor.getValue().getMaskObjectKey()).isEqualTo("tasks/100/mask.png");
        assertThat(resultCaptor.getValue().getResultJson()).contains("vesselAreaRatio");
        verify(logMapper, org.mockito.Mockito.times(2)).insert(any(LogEntity.class));
    }

    @Test
    void processMarksUnsupportedTaskTypeFailedWithoutCallingAi() {
        TaskEntity task = task(101L, TaskType.IMAGE_QUALITY_CHECK);
        when(taskMapper.selectById(101L)).thenReturn(task);
        when(taskMapper.claimForExecution(org.mockito.ArgumentMatchers.eq(101L), any())).thenReturn(1);

        ExecutionDisposition disposition = service.process(message(101L, TaskType.IMAGE_QUALITY_CHECK));

        assertThat(disposition).isEqualTo(ExecutionDisposition.FAILED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorMessage()).contains("暂不支持");
        verify(aiInferenceClient, never()).segment(any(), any(), any());
    }

    @Test
    void processRecordsInferenceFailure() throws Exception {
        TaskEntity task = task(102L, TaskType.VESSEL_SEGMENTATION);
        Path image = imageRoot.resolve("source.png");
        Files.write(image, new byte[]{1});
        when(taskMapper.selectById(102L)).thenReturn(task);
        when(taskMapper.claimForExecution(org.mockito.ArgumentMatchers.eq(102L), any())).thenReturn(1);
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder()
                .id(20L)
                .status(ImageStatus.UPLOADED)
                .originalFilename("source.png")
                .fileType("image/png")
                .storageObjectKey("source.png")
                .build());
        when(aiInferenceClient.segment(image, "source.png", "image/png"))
                .thenThrow(new AiInferenceException("AI unavailable"));

        ExecutionDisposition disposition = service.process(message(102L, TaskType.VESSEL_SEGMENTATION));

        assertThat(disposition).isEqualTo(ExecutionDisposition.FAILED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorMessage()).contains("AI unavailable");
        verify(analysisResultMapper, never()).insert(any(AnalysisResultEntity.class));
    }

    @Test
    void processIgnoresTaskWhenAtomicClaimLosesRace() {
        TaskEntity task = task(103L, TaskType.VESSEL_SEGMENTATION);
        when(taskMapper.selectById(103L)).thenReturn(task);

        ExecutionDisposition disposition = service.process(message(103L, TaskType.VESSEL_SEGMENTATION));

        assertThat(disposition).isEqualTo(ExecutionDisposition.IGNORED);
        verify(aiInferenceClient, never()).segment(any(), any(), any());
        verify(logMapper, never()).insert(any(LogEntity.class));
    }

    private TaskEntity task(Long id, TaskType taskType) {
        return TaskEntity.builder()
                .id(id)
                .imageFileId(20L)
                .taskType(taskType)
                .status(TaskStatus.WAITING)
                .retryCount(0)
                .maxRetryCount(3)
                .build();
    }

    private AnalysisTaskMessage message(Long taskId, TaskType taskType) {
        return AnalysisTaskMessage.builder().taskId(taskId).taskType(taskType).build();
    }

    private AiInferenceResponse inferenceResponse() {
        AiInferenceResponse response = new AiInferenceResponse();
        response.setInferenceId("abc123");
        response.setResultType("VESSEL_SEGMENTATION");
        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("vesselAreaRatio", 0.25);
        resultJson.put("conclusion", "ok");
        response.setResultJson(resultJson);
        response.setModelName("FSCNet_Final_DMI");
        response.setModelVersion("v1");
        response.setProcessingTimeMs(12);
        response.setMaskUrl("/v1/artifacts/abc123/mask");
        return response;
    }
}
