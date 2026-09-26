package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmException;
import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.llm.PromptScenario;
import com.example.retinavision.mapper.AnalysisReportMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.AnalysisResultComparisonVO;
import com.example.retinavision.pojo.VO.CaseAnalysisTimelineVO;
import com.example.retinavision.pojo.VO.CaseTrendSummaryVO;
import com.example.retinavision.service.CaseAnalysisTimelineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CaseAnalysisTimelineServiceImpl implements CaseAnalysisTimelineService {
    private static final String TEMPLATE_CODE = "CASE_TREND_SUMMARY";

    private final TaskMapper tasks;
    private final AnalysisResultMapper results;
    private final CaseMapper cases;
    private final ImageMapper images;
    private final AnalysisReviewMapper reviews;
    private final AnalysisReportMapper reports;
    private final LlmOrchestrationService llm;
    private final LlmSafetyPolicy safetyPolicy;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public CaseAnalysisTimelineServiceImpl(TaskMapper tasks,
                                           AnalysisResultMapper results,
                                           CaseMapper cases,
                                           ImageMapper images,
                                           AnalysisReviewMapper reviews,
                                           AnalysisReportMapper reports,
                                           LlmOrchestrationService llm,
                                           LlmSafetyPolicy safetyPolicy) {
        this.tasks = tasks;
        this.results = results;
        this.cases = cases;
        this.images = images;
        this.reviews = reviews;
        this.reports = reports;
        this.llm = llm;
        this.safetyPolicy = safetyPolicy;
    }

    CaseAnalysisTimelineServiceImpl(TaskMapper tasks,
                                    AnalysisResultMapper results,
                                    CaseMapper cases,
                                    ImageMapper images,
                                    AnalysisReviewMapper reviews,
                                    AnalysisReportMapper reports) {
        this(tasks, results, cases, images, reviews, reports, null, new LlmSafetyPolicy());
    }

    @Override
    public CaseAnalysisTimelineVO timeline(Long caseId, EyeSide eyeSide, TaskType taskType, LocalDateTime startTime, LocalDateTime endTime) {
        CaseEntity caseEntity = requireCase(caseId);
        if (eyeSide != null && caseEntity.getEyeSide() != null && eyeSide != caseEntity.getEyeSide()) {
            return new CaseAnalysisTimelineVO(caseId, caseEntity.getEyeSide(), List.of());
        }

        List<TaskEntity> taskList = tasks.selectList(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getCaseId, caseId));
        List<TaskEntity> filteredTasks = taskList.stream()
                .filter(task -> taskType == null || task.getTaskType() == taskType)
                .filter(task -> inRange(eventTime(task), startTime, endTime))
                .sorted(Comparator.comparing(this::eventTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<Long> taskIds = filteredTasks.stream().map(TaskEntity::getId).filter(Objects::nonNull).toList();
        List<Long> imageIds = filteredTasks.stream().map(TaskEntity::getImageFileId).filter(Objects::nonNull).distinct().toList();
        Map<Long, AnalysisResultEntity> resultByTask = taskIds.isEmpty() ? Map.of() : results.selectList(new LambdaQueryWrapper<AnalysisResultEntity>()
                        .in(AnalysisResultEntity::getTaskId, taskIds))
                .stream().collect(Collectors.toMap(AnalysisResultEntity::getTaskId, Function.identity(), (first, second) -> second));
        Map<Long, ImageFileEntity> imageById = imageIds.isEmpty() ? Map.of() : images.selectBatchIds(imageIds).stream()
                .collect(Collectors.toMap(ImageFileEntity::getId, Function.identity(), (first, second) -> first));
        List<Long> resultIds = resultByTask.values().stream().map(AnalysisResultEntity::getId).filter(Objects::nonNull).toList();
        Map<Long, AnalysisReviewEntity> reviewByResult = reviewMap(resultIds);
        Map<Long, AnalysisReportEntity> reportByResult = reportMap(resultIds);

        List<CaseAnalysisTimelineVO.Item> items = filteredTasks.stream()
                .map(task -> item(task, resultByTask.get(task.getId()), imageById.get(task.getImageFileId()),
                        reviewByResult, reportByResult))
                .toList();
        return new CaseAnalysisTimelineVO(caseId, caseEntity.getEyeSide(), items);
    }

    @Override
    public AnalysisResultComparisonVO compare(Long baselineResultId, Long targetResultId) {
        AnalysisResultEntity baseline = requireResult(baselineResultId);
        AnalysisResultEntity target = requireResult(targetResultId);
        TaskEntity baselineTask = requireTask(baseline.getTaskId());
        TaskEntity targetTask = requireTask(target.getTaskId());
        CaseEntity baselineCase = requireCase(baselineTask.getCaseId());
        CaseEntity targetCase = requireCase(targetTask.getCaseId());
        if (!Objects.equals(baselineTask.getCaseId(), targetTask.getCaseId())
                || baselineTask.getTaskType() != targetTask.getTaskType()
                || baselineCase.getEyeSide() != targetCase.getEyeSide()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "只能比较同一病例、同一眼别、同类任务的分析结果");
        }

        ImageFileEntity baselineImage = baselineTask.getImageFileId() == null ? null : images.selectById(baselineTask.getImageFileId());
        ImageFileEntity targetImage = targetTask.getImageFileId() == null ? null : images.selectById(targetTask.getImageFileId());
        Map<Long, AnalysisReviewEntity> reviewByResult = reviewMap(List.of(baselineResultId, targetResultId));
        Map<Long, AnalysisReportEntity> reportByResult = reportMap(List.of(baselineResultId, targetResultId));

        AnalysisResultComparisonVO.Snapshot baselineSnapshot = snapshot(baseline, baselineTask, baselineImage,
                reviewByResult.get(baselineResultId), reportByResult.get(baselineResultId));
        AnalysisResultComparisonVO.Snapshot targetSnapshot = snapshot(target, targetTask, targetImage,
                reviewByResult.get(targetResultId), reportByResult.get(targetResultId));

        Double qualityDelta = delta(baselineSnapshot.qualityScore(), targetSnapshot.qualityScore());
        Double vesselDelta = delta(baselineSnapshot.vesselAreaRatio(), targetSnapshot.vesselAreaRatio());
        List<String> notes = new ArrayList<>();
        if (qualityDelta == null || vesselDelta == null) {
            notes.add("部分指标缺失，未计算对应差值。");
        }
        if (!Objects.equals(baselineSnapshot.modelVersion(), targetSnapshot.modelVersion())) {
            notes.add("两次结果模型版本不同，趋势变化需谨慎解读。");
        }
        return new AnalysisResultComparisonVO(
                baselineTask.getCaseId(),
                baselineCase.getEyeSide(),
                baselineTask.getTaskType(),
                baselineSnapshot,
                targetSnapshot,
                qualityDelta,
                vesselDelta,
                !Objects.equals(baselineSnapshot.modelName(), targetSnapshot.modelName())
                        || !Objects.equals(baselineSnapshot.modelVersion(), targetSnapshot.modelVersion()),
                baselineSnapshot.reviewStatus() != targetSnapshot.reviewStatus(),
                baselineSnapshot.reportStatus() != targetSnapshot.reportStatus(),
                notes
        );
    }

    @Override
    public CaseTrendSummaryVO generateTrendSummary(Long caseId, EyeSide eyeSide, TaskType taskType) {
        if (llm == null) {
            throw new LlmException("LLM trend summary generation is unavailable");
        }
        CaseAnalysisTimelineVO timeline = timeline(caseId, eyeSide, taskType, null, null);
        LlmGenerationResult generation = llm.generateJson(TEMPLATE_CODE, summaryUserPrompt(timeline));
        JsonNode node;
        try {
            node = json.readTree(generation.content());
        } catch (Exception exception) {
            throw new LlmException("LLM trend summary must be valid JSON", exception);
        }
        String summary = safeText(node.path("summary").asText(""));
        String recommendation = safeText(node.path("recommendation").asText(""));
        safetyPolicy.requireSafe(PromptScenario.CASE_TREND_SUMMARY, summary, recommendation);
        return new CaseTrendSummaryVO(caseId, summary, recommendation,
                safetyPolicy.disclaimer(PromptScenario.CASE_TREND_SUMMARY),
                generation.provider(), generation.model(), LocalDateTime.now());
    }

    private CaseAnalysisTimelineVO.Item item(TaskEntity task,
                                             AnalysisResultEntity result,
                                             ImageFileEntity image,
                                             Map<Long, AnalysisReviewEntity> reviewByResult,
                                             Map<Long, AnalysisReportEntity> reportByResult) {
        AnalysisReviewEntity review = result == null ? null : reviewByResult.get(result.getId());
        AnalysisReportEntity report = result == null ? null : reportByResult.get(result.getId());
        return new CaseAnalysisTimelineVO.Item(
                task.getId(),
                task.getTaskNo(),
                task.getTaskType(),
                task.getStatus(),
                task.getImageFileId(),
                result == null ? null : result.getId(),
                image == null ? null : image.getQualityScore(),
                image == null ? null : image.getQualityStatus(),
                image == null ? null : image.getQualityTaskId(),
                image == null ? null : image.getQualityResultId(),
                image == null ? null : image.getQualityCheckedAt(),
                result == null ? null : vesselAreaRatio(result.getResultJson()),
                result == null ? null : result.getModelName(),
                result == null ? null : result.getModelVersion(),
                result == null ? null : result.getProcessingTimeMs(),
                review == null ? null : review.getStatus(),
                report == null ? null : report.getStatus(),
                report == null ? null : report.getVersion(),
                task.getSubmittedAt(),
                task.getFinishedAt(),
                result == null ? null : result.getCreatedAt()
        );
    }

    private AnalysisResultComparisonVO.Snapshot snapshot(AnalysisResultEntity result,
                                                         TaskEntity task,
                                                         ImageFileEntity image,
                                                         AnalysisReviewEntity review,
                                                         AnalysisReportEntity report) {
        return new AnalysisResultComparisonVO.Snapshot(
                result.getId(),
                task.getId(),
                task.getImageFileId(),
                image == null ? null : image.getQualityScore(),
                image == null ? null : image.getQualityStatus(),
                vesselAreaRatio(result.getResultJson()),
                result.getModelName(),
                result.getModelVersion(),
                review == null ? null : review.getStatus(),
                report == null ? null : report.getStatus(),
                task.getFinishedAt(),
                result.getCreatedAt()
        );
    }

    private Map<Long, AnalysisReviewEntity> reviewMap(List<Long> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            return Map.of();
        }
        return reviews.selectList(new LambdaQueryWrapper<AnalysisReviewEntity>()
                        .in(AnalysisReviewEntity::getResultId, resultIds))
                .stream()
                .collect(Collectors.toMap(AnalysisReviewEntity::getResultId, Function.identity(), (first, second) -> second));
    }

    private Map<Long, AnalysisReportEntity> reportMap(List<Long> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            return Map.of();
        }
        return reports.selectList(new LambdaQueryWrapper<AnalysisReportEntity>()
                        .in(AnalysisReportEntity::getResultId, resultIds)
                        .in(AnalysisReportEntity::getStatus, List.of(ReportStatus.SIGNED, ReportStatus.SUPERSEDED)))
                .stream()
                .collect(Collectors.toMap(AnalysisReportEntity::getResultId, Function.identity(), this::latestReport));
    }

    private AnalysisReportEntity latestReport(AnalysisReportEntity first, AnalysisReportEntity second) {
        Integer firstVersion = first.getVersion() == null ? 0 : first.getVersion();
        Integer secondVersion = second.getVersion() == null ? 0 : second.getVersion();
        return secondVersion >= firstVersion ? second : first;
    }

    private CaseEntity requireCase(Long caseId) {
        if (caseId == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "病例 ID 不能为空");
        }
        CaseEntity entity = cases.selectById(caseId);
        if (entity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "病例不存在");
        }
        return entity;
    }

    private AnalysisResultEntity requireResult(Long resultId) {
        AnalysisResultEntity entity = results.selectById(resultId);
        if (entity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析结果不存在");
        }
        return entity;
    }

    private TaskEntity requireTask(Long taskId) {
        TaskEntity entity = taskId == null ? null : tasks.selectById(taskId);
        if (entity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析任务不存在");
        }
        return entity;
    }

    private LocalDateTime eventTime(TaskEntity task) {
        if (task.getFinishedAt() != null) {
            return task.getFinishedAt();
        }
        if (task.getStartedAt() != null) {
            return task.getStartedAt();
        }
        return task.getSubmittedAt();
    }

    private boolean inRange(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        if (value == null) {
            return true;
        }
        return (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    private Double vesselAreaRatio(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = json.readTree(resultJson);
            JsonNode value = node.path("vesselAreaRatio");
            return value.isNumber() ? value.asDouble() : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private Double delta(Double baseline, Double target) {
        return baseline == null || target == null ? null : target - baseline;
    }

    private String summaryUserPrompt(CaseAnalysisTimelineVO timeline) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("caseId", timeline.caseId());
            payload.put("eyeSide", timeline.eyeSide());
            payload.put("items", timeline.items().stream().map(item -> {
                Map<String, Object> map = new HashMap<>();
                map.put("taskId", item.taskId());
                map.put("taskType", item.taskType());
                map.put("taskStatus", item.taskStatus());
                map.put("qualityScore", item.qualityScore());
                map.put("qualityStatus", item.qualityStatus());
                map.put("vesselAreaRatio", item.vesselAreaRatio());
                map.put("modelName", item.modelName());
                map.put("modelVersion", item.modelVersion());
                map.put("reviewStatus", item.reviewStatus());
                map.put("reportStatus", item.reportStatus());
                map.put("finishedAt", item.finishedAt());
                return map;
            }).toList());
            return json.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new LlmException("Could not build trend summary prompt", exception);
        }
    }

    private String safeText(String value) {
        return safetyPolicy.requireText(value, 1000, "LLM trend summary is missing required text");
    }
}
