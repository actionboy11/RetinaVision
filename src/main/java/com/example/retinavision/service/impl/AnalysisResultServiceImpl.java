package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.VO.ImageQualitySummaryVO;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.AnalysisResultVO;
import com.example.retinavision.service.AnalysisResultService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class AnalysisResultServiceImpl implements AnalysisResultService {
    private final TaskMapper taskMapper;
    private final AnalysisResultMapper analysisResultMapper;
    private final ObjectMapper objectMapper;
    private final ImageMapper imageMapper;
    private final Path resultRootPath;

    public AnalysisResultServiceImpl(
            TaskMapper taskMapper,
            AnalysisResultMapper analysisResultMapper,
            ImageMapper imageMapper,
            ObjectMapper objectMapper,
            // 读取配置文件中的分析结果存储根路径，默认值为 "uploads/results"
            @Value("${retina.upload.result-root:uploads/results}") String resultRoot) {
        this.taskMapper = taskMapper;
        this.analysisResultMapper = analysisResultMapper;
        this.imageMapper = imageMapper;
        this.objectMapper = objectMapper;
        this.resultRootPath = Paths.get(resultRoot).toAbsolutePath().normalize();
    }
    @Override
    public AnalysisResultVO getAnalysisResult(Long taskId) {
        // 这里应该调用数据库查询分析结果的逻辑
        TaskEntity taskEntity = taskMapper.selectById(taskId);
        if (taskEntity == null) {
            throw  new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.TASK_NOT_EXISTS);
        }
        if (taskEntity.getStatus() != TaskStatus.SUCCESS) {
                throw new BaseException(ErrorMessageSignal.CONFLICT, "任务未完成，无法获取分析结果");
        }
        // 查询分析结果
        AnalysisResultEntity resultEntity = analysisResultMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultEntity>()
                        .eq(AnalysisResultEntity::getTaskId, taskId)
        );
        if (resultEntity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析结果不存在");
        }
        // 解析分析结果 JSON 字符串为 Map<String, Object>
        // objectMapper 是 Jackson 提供的 JSON 处理工具类，用于将 JSON 字符串转换为 Java 对象。
        // TypeReference<Map<String, Object>>() 是一个类型引用，用于指定目标对象的类型为 Map<String, Object>
        // 这里使用 TypeReference 是因为 Jackson 不支持直接使用 Map 类型，而需要通过 TypeReference 来指定目标对象的类型。
        Map<String, Object> resultJsonMap;
        try {
            resultJsonMap = objectMapper.readValue(
                    resultEntity.getResultJson(),
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (JsonProcessingException e) {
            throw new BaseException(
                    ErrorMessageSignal.SERVER_ERROR,
                    "分析结果 JSON 解析失败"
            );
        }
        // 转换为VO
        ImageFileEntity image = imageMapper.selectById(taskEntity.getImageFileId());
        ImageQualitySummaryVO qualitySummary = image == null ? null : new ImageQualitySummaryVO(
                image.getQualityStatus(), image.getQualityScore(), image.getQualityResultId(),
                image.getQualityTaskId(), image.getQualityCheckedAt());
        return AnalysisResultVO.builder()
                .id(resultEntity.getId())
                .taskId(resultEntity.getTaskId())
                .resultJson(resultJsonMap)
                .resultType(resultEntity.getResultType())
                .maskPreviewUrl(resultEntity.getMaskPreviewUrl())
                .reportDownloadUrl(resultEntity.getReportDownloadUrl())
                .modelName(resultEntity.getModelName())
                .modelVersion(resultEntity.getModelVersion())
                .qualitySummary(qualitySummary)
                .processingTimeMs(resultEntity.getProcessingTimeMs())
                .createdAt(resultEntity.getCreatedAt())
                .updatedAt(resultEntity.getUpdatedAt())
                .build();
    }

    @Override
    public Path getResultMaskPath(Long resultId) {
        AnalysisResultEntity resultEntity = getResultEntity(resultId);
        if (!StringUtils.hasText(resultEntity.getMaskObjectKey())) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分割结果图不存在");
        }
        return resolveStoredResultPath(resultEntity.getMaskObjectKey(), "分割结果图文件不存在");
    }

    @Override
    public Path getResultReportPath(Long resultId) {
        AnalysisResultEntity resultEntity = getResultEntity(resultId);
        if (!StringUtils.hasText(resultEntity.getReportObjectKey())) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析报告不存在");
        }
        return resolveStoredResultPath(resultEntity.getReportObjectKey(), "分析报告文件不存在");
    }

    private AnalysisResultEntity getResultEntity(Long resultId) {
        AnalysisResultEntity resultEntity = analysisResultMapper.selectById(resultId);
        if (resultEntity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析结果不存在");
        }
        return resultEntity;
    }

    private Path resolveStoredResultPath(String objectKey, String notFoundMessage) {
        //resolve 方法用于将路径片段拼接到根路径上，得到完整的路径，normalize 方法用于归一化路径
        Path targetPath = resultRootPath.resolve(objectKey).normalize();
        if (!targetPath.startsWith(resultRootPath)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "分析结果文件路径无效");
        }
        // isRegularFile 方法用于检查它是不是普通文件,而不是目录或其他类型的文件
        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, notFoundMessage);
        }
        return targetPath;
    }

}
