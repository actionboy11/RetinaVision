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
import com.example.retinavision.service.AnalysisTaskExecutionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class AnalysisTaskExecutionServiceImpl implements AnalysisTaskExecutionService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskExecutionServiceImpl.class);
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
        // 查询任务实体，检查任务状态是否可处理，是否失败，是否已取消
        TaskEntity task = taskMapper.selectById(message.getTaskId());
        if (task == null || task.getStatus() == TaskStatus.FAILED) {
            return ExecutionDisposition.REQUEUE;
        }
        if (!CONSUMABLE_STATUSES.contains(task.getStatus())) {
            return ExecutionDisposition.IGNORED;
        }
        // 通过条件更新原子抢占任务；返回 0 表示其他消费者已经抢先处理(任务幂等抢占)
        //幂等可以简单理解为：同一个操作执行一次或重复执行多次，最终业务结果一致。
        if (!claimForExecution(task)) {
            return ExecutionDisposition.IGNORED;
        }
        String requestId = "task-" + task.getId() + "-" + UUID.randomUUID();
        long attemptStarted = System.nanoTime();
        try {
            if (task.getTaskType() != TaskType.VESSEL_SEGMENTATION) {
                throw new IllegalStateException("暂不支持任务类型：" + task.getTaskType());
            }
            ImageFileEntity image = getAvailableImage(task.getImageFileId());
            // 解析图像文件路径(后端服务器上的原图实际路径)
            Path imagePath = resolveImagePath(image.getStorageObjectKey());
            // 调用 AI 血管分割服务
            AiInferenceResponse response = aiInferenceClient.segment(
                    imagePath,
                    image.getOriginalFilename(),
                    image.getFileType(),
                    requestId
            );
            //response.getMaskUrl() 得到的是 Python AI 服务返回的 HTTP 相对地址，不是 Windows 文件路径
            byte[] maskBytes = aiInferenceClient.downloadMask(response.getMaskUrl(), requestId);
            // 将分割结果图存储到本地文件系统，并返回存储路径
            String maskObjectKey = storeMask(task.getId(), maskBytes);
            // 将分析结果和分割图信息持久化到数据库，更新任务状态为 SUCCESS，并记录完成时间和日志
            persistSuccess(task, response, maskObjectKey);
            log.info(
                    "AI task succeeded taskId={} taskNo={} retryCount={} requestId={} durationMs={}",
                    task.getId(), task.getTaskNo(), task.getRetryCount(), requestId,
                    elapsedMillis(attemptStarted)
            );
            return ExecutionDisposition.SUCCESS;
        } catch (Exception exception) {
            // 将任务状态更新为 FAILED，并记录失败时间和日志
            persistFailure(task, exception);
            String category = exception instanceof AiInferenceException aiException
                    ? aiException.getCategory().name()
                    : "UNKNOWN";
            log.warn(
                    "AI task failed taskId={} taskNo={} retryCount={} requestId={} category={} durationMs={}",
                    task.getId(), task.getTaskNo(), task.getRetryCount(), requestId, category,
                    elapsedMillis(attemptStarted)
            );
            return ExecutionDisposition.FAILED;
        }
    }

    private boolean claimForExecution(TaskEntity task) {
        TaskStatus fromStatus = task.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.claimForExecution(task.getId(), now) != 1) {
            return false;
        }
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        task.setErrorMessage(null);
        insertLog(task.getId(), fromStatus, TaskStatus.RUNNING, "AI Worker 已接收任务，开始处理", now);
        return true;
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
        // 生成分割结果图的存储路径，格式为 tasks/{taskId}/mask.png
        String objectKey = "tasks/" + taskId + "/mask.png";
        Path target = resultRootPath.resolve(objectKey).normalize();
        if (!target.startsWith(resultRootPath)) {
            throw new IllegalStateException("分割结果图存储路径无效");
        }
        // 创建必要的目录结构，并将分割结果图写入文件系统
        Files.createDirectories(target.getParent());
        // 将分割结果图写入文件系统，
        // Files.write 方法会将 byte[] 数组写入指定路径的文件，如果文件不存在则创建，如果文件已存在则覆盖
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
        // 对错误消息进行归一化处理，将换行符替换为空空格，避免数据库存储过长的错误消息
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

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
