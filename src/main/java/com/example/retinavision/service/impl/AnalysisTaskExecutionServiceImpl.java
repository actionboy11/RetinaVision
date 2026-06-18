package com.example.retinavision.service.impl;

import com.example.retinavision.ai.AiInferenceClient;
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
import com.example.retinavision.service.AnalysisTaskExecutionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Service
public class AnalysisTaskExecutionServiceImpl implements AnalysisTaskExecutionService {
    private static final Set<TaskStatus> CONSUMABLE_STATUSES =
            EnumSet.of(TaskStatus.WAITING, TaskStatus.RETRYING);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final TaskMapper taskMapper;
    private final ImageMapper imageMapper;
    private final AnalysisResultMapper analysisResultMapper;
    private final LogMapper logMapper;
    private final AiInferenceClient aiInferenceClient;
    private final ObjectMapper objectMapper;
    private final Path imageRootPath;
    private final Path resultRootPath;

    public AnalysisTaskExecutionServiceImpl(
            TaskMapper taskMapper,
            ImageMapper imageMapper,
            AnalysisResultMapper analysisResultMapper,
            LogMapper logMapper,
            AiInferenceClient aiInferenceClient,
            ObjectMapper objectMapper,
            @Value("${retina.upload.image-root:uploads/images}") String imageRoot,
            @Value("${retina.upload.result-root:uploads/results}") String resultRoot) {
        this.taskMapper = taskMapper;
        this.imageMapper = imageMapper;
        this.analysisResultMapper = analysisResultMapper;
        this.logMapper = logMapper;
        this.aiInferenceClient = aiInferenceClient;
        this.objectMapper = objectMapper;
        this.imageRootPath = Paths.get(imageRoot).toAbsolutePath().normalize();
        this.resultRootPath = Paths.get(resultRoot).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public ExecutionDisposition process(AnalysisTaskMessage message) {
        if (message == null || message.getTaskId() == null) {
            return ExecutionDisposition.IGNORED;
        }
        TaskEntity task = taskMapper.selectById(message.getTaskId());
        if (task == null || task.getStatus() == TaskStatus.FAILED) {
            return ExecutionDisposition.REQUEUE;
        }
        if (!CONSUMABLE_STATUSES.contains(task.getStatus())) {
            return ExecutionDisposition.IGNORED;
        }

        transitionToRunning(task);
        try {
            if (task.getTaskType() != TaskType.VESSEL_SEGMENTATION) {
                throw new IllegalStateException("暂不支持任务类型：" + task.getTaskType());
            }
            ImageFileEntity image = getAvailableImage(task.getImageFileId());
            Path imagePath = resolveImagePath(image.getStorageObjectKey());
            AiInferenceResponse response = aiInferenceClient.segment(
                    imagePath,
                    image.getOriginalFilename(),
                    image.getFileType()
            );
            byte[] maskBytes = aiInferenceClient.downloadMask(response.getMaskUrl());
            String maskObjectKey = storeMask(task.getId(), maskBytes);
            persistSuccess(task, response, maskObjectKey);
            return ExecutionDisposition.SUCCESS;
        } catch (Exception exception) {
            persistFailure(task, exception);
            return ExecutionDisposition.FAILED;
        }
    }

    private void transitionToRunning(TaskEntity task) {
        TaskStatus fromStatus = task.getStatus();
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        task.setErrorMessage(null);
        taskMapper.updateById(task);
        insertLog(task.getId(), fromStatus, TaskStatus.RUNNING, "AI Worker 已接收任务，开始处理", now);
    }

    private ImageFileEntity getAvailableImage(Long imageFileId) {
        ImageFileEntity image = imageMapper.selectById(imageFileId);
        if (image == null || image.getDeletedAt() != null || image.getStatus() == ImageStatus.DELETED) {
            throw new IllegalStateException("任务关联图像不存在或已删除");
        }
        return image;
    }

    private Path resolveImagePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException("任务关联图像存储路径为空");
        }
        Path imagePath = imageRootPath.resolve(objectKey).normalize();
        if (!imagePath.startsWith(imageRootPath)) {
            throw new IllegalStateException("任务关联图像存储路径无效");
        }
        if (!Files.isRegularFile(imagePath)) {
            throw new IllegalStateException("任务关联图像文件不存在");
        }
        return imagePath;
    }

    private String storeMask(Long taskId, byte[] maskBytes) throws IOException {
        if (maskBytes == null || maskBytes.length == 0) {
            throw new IllegalStateException("AI 服务返回了空的分割结果图");
        }
        String objectKey = "tasks/" + taskId + "/mask.png";
        Path target = resultRootPath.resolve(objectKey).normalize();
        if (!target.startsWith(resultRootPath)) {
            throw new IllegalStateException("分割结果图存储路径无效");
        }
        Files.createDirectories(target.getParent());
        Files.write(target, maskBytes);
        return objectKey;
    }

    private void persistSuccess(TaskEntity task, AiInferenceResponse response, String maskObjectKey)
            throws JsonProcessingException {
        LocalDateTime now = LocalDateTime.now();
        AnalysisResultEntity result = AnalysisResultEntity.builder()
                .taskId(task.getId())
                .resultType(TaskType.VESSEL_SEGMENTATION)
                .resultJson(objectMapper.writeValueAsString(response.getResultJson()))
                .maskBucket("local")
                .maskObjectKey(maskObjectKey)
                .maskPreviewUrl(null)
                .reportBucket(null)
                .reportObjectKey(null)
                .reportDownloadUrl(null)
                .modelName(response.getModelName())
                .modelVersion(response.getModelVersion())
                .processingTimeMs(response.getProcessingTimeMs())
                .createdAt(now)
                .updatedAt(now)
                .build();
        analysisResultMapper.insert(result);

        task.setStatus(TaskStatus.SUCCESS);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        task.setErrorMessage(null);
        taskMapper.updateById(task);
        insertLog(task.getId(), TaskStatus.RUNNING, TaskStatus.SUCCESS, "血管分割完成", now);
    }

    private void persistFailure(TaskEntity task, Exception exception) {
        LocalDateTime now = LocalDateTime.now();
        String errorMessage = boundedErrorMessage(exception);
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        insertLog(task.getId(), TaskStatus.RUNNING, TaskStatus.FAILED, errorMessage, now);
    }

    private String boundedErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        String normalized = "AI 任务执行失败：" + message.replaceAll("[\\r\\n]+", " ");
        return normalized.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private void insertLog(
            Long taskId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            String message,
            LocalDateTime createdAt) {
        logMapper.insert(LogEntity.builder()
                .taskId(taskId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .message(message)
                .operatorType("WORKER")
                .operatorId(null)
                .createdAt(createdAt)
                .build());
    }
}
