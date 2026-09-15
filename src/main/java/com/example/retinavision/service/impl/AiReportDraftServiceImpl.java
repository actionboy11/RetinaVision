package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmException;
import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.llm.PromptScenario;
import com.example.retinavision.mapper.AnalysisCorrectionMapper;
import com.example.retinavision.mapper.AnalysisReportMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.AnalysisCorrectionEntity;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.service.AiReportDraftService;
import com.example.retinavision.service.ClinicalTaskLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiReportDraftServiceImpl implements AiReportDraftService {
    private static final String TEMPLATE_CODE = "REPORT_DRAFT_GENERATION";
    private static final int TEXT_LIMIT = 1000;

    private final AnalysisResultMapper results;
    private final AnalysisReportMapper reports;
    private final AnalysisReviewMapper reviews;
    private final TaskMapper tasks;
    private final CaseMapper cases;
    private final ImageMapper images;
    private final AnalysisCorrectionMapper corrections;
    private final ClinicalTaskLogService clinicalLogs;
    private final LlmOrchestrationService llm;
    private final LlmSafetyPolicy safetyPolicy;
    private final ObjectMapper json = new ObjectMapper();

    // public 的构造函数是给 Spring 依赖注入 设计的
    @Autowired
    public AiReportDraftServiceImpl(AnalysisResultMapper results,
                                    AnalysisReportMapper reports,
                                    AnalysisReviewMapper reviews,
                                    TaskMapper tasks,
                                    CaseMapper cases,
                                    ImageMapper images,
                                    AnalysisCorrectionMapper corrections,
                                    ClinicalTaskLogService clinicalLogs,
                                    LlmOrchestrationService llm,
                                    LlmSafetyPolicy safetyPolicy) {
        this.results = results;
        this.reports = reports;
        this.reviews = reviews;
        this.tasks = tasks;
        this.cases = cases;
        this.images = images;
        this.corrections = corrections;
        this.clinicalLogs = clinicalLogs;
        this.llm = llm;
        this.safetyPolicy = safetyPolicy;
    }

    @Override
    @Transactional
    public AnalysisReportEntity generateDraft(Long resultId, Integer userId) {
        AnalysisResultEntity result = requireResult(resultId);
        AnalysisReportEntity draft = currentDraft(resultId, userId);
        // LLM 生成的报告 JSON 字符串 存储在 draft.draftJson 中
        LlmGenerationResult generation = llm.generateJson(TEMPLATE_CODE, userPrompt(result));
        Map<String, Object> draftJson = validatedDraft(generation.content());
        draftJson.put("resultId", result.getId());
        draftJson.put("resultType", result.getResultType() == null ? null : result.getResultType().name());
        draftJson.put("modelName", result.getModelName());
        draftJson.put("modelVersion", result.getModelVersion());
        draftJson.put("processingTimeMs", result.getProcessingTimeMs());
        draftJson.put("llmProvider", generation.provider());
        draftJson.put("llmModel", generation.model());
        draftJson.put("promptTemplateCode", generation.templateCode());
        draftJson.put("promptTemplateVersion", generation.templateVersion());
        draftJson.put("llmGeneratedAt", LocalDateTime.now().toString());
        try {
            draft.setDraftJson(json.writeValueAsString(draftJson));
        } catch (Exception exception) {
            throw new LlmException("LLM draft could not be serialized", exception);
        }
        draft.setUpdatedAt(LocalDateTime.now());
        reports.updateById(draft);
        clinicalLogs.appendResultEvent(resultId, "AI 草稿已生成，仅供医生审核参考", "USER", userId);
        return draft;
    }

    private AnalysisResultEntity requireResult(Long resultId) {
        AnalysisResultEntity result = results.selectById(resultId);
        if (result == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析结果不存在");
        }
        return result;
    }

    //reduce((first, second) -> second) 取最后一个草稿版本，如果没有草稿则创建一个新的草稿
    private AnalysisReportEntity currentDraft(Long resultId, Integer userId) {
        List<AnalysisReportEntity> reportList = reports.selectList(new LambdaQueryWrapper<AnalysisReportEntity>()
                .eq(AnalysisReportEntity::getResultId, resultId)
                .orderByAsc(AnalysisReportEntity::getVersion));
        return reportList.stream()
                .filter(report -> report.getStatus() == ReportStatus.DRAFT)
                .reduce((first, second) -> second)
                .orElseGet(() -> createDraft(resultId, userId, reportList));
    }

    private AnalysisReportEntity createDraft(Long resultId, Integer userId, List<AnalysisReportEntity> reportList) {
        AnalysisReportEntity draft = new AnalysisReportEntity();
        LocalDateTime now = LocalDateTime.now();
        draft.setResultId(resultId);
        draft.setVersion(reportList.stream().mapToInt(AnalysisReportEntity::getVersion).max().orElse(0) + 1);
        draft.setStatus(ReportStatus.DRAFT);
        draft.setDraftJson("{}");
        draft.setCreatedBy(userId);
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        reports.insert(draft);
        return draft;
    }

    private String userPrompt(AnalysisResultEntity result) {
        try {
            //
            return json.writeValueAsString(context(result));
        } catch (Exception exception) {
            throw new LlmException("Could not build LLM prompt context", exception);
        }
    }

    //构建 LLM 提示上下文 ，包含分析结果的所有信息
    private Map<String, Object> context(AnalysisResultEntity result) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("result", Map.of(
                "id", result.getId(),
                "type", result.getResultType() == null ? "" : result.getResultType().name(),
                "resultJson", readTree(result.getResultJson()),
                "modelName", safe(result.getModelName()),
                "modelVersion", safe(result.getModelVersion()),
                "processingTimeMs", result.getProcessingTimeMs() == null ? 0 : result.getProcessingTimeMs()
        ));

        TaskEntity task = result.getTaskId() == null ? null : tasks.selectById(result.getTaskId());
        CaseEntity caseEntity = task == null || task.getCaseId() == null ? null : cases.selectById(task.getCaseId());
        ImageFileEntity image = task == null || task.getImageFileId() == null ? null : images.selectById(task.getImageFileId());
        context.put("case", Map.of(
                "age", caseEntity == null || caseEntity.getPatientAge() == null ? "" : caseEntity.getPatientAge(),
                "gender", caseEntity == null || caseEntity.getPatientGender() == null ? "" : caseEntity.getPatientGender().name(),
                "eyeSide", caseEntity == null || caseEntity.getEyeSide() == null ? "" : caseEntity.getEyeSide().name()
        ));
        context.put("image", Map.of(
                "qualityStatus", image == null || image.getQualityStatus() == null ? "" : image.getQualityStatus().name(),
                "qualityScore", image == null || image.getQualityScore() == null ? "" : image.getQualityScore(),
                "width", image == null || image.getImageWidth() == null ? "" : image.getImageWidth(),
                "height", image == null || image.getImageHeight() == null ? "" : image.getImageHeight()
        ));

        AnalysisReviewEntity review = reviews.selectOne(new LambdaQueryWrapper<AnalysisReviewEntity>()
                .eq(AnalysisReviewEntity::getResultId, result.getId()));
        if (review != null) {
            context.put("review", Map.of(
                    "status", review.getStatus() == null ? "" : review.getStatus().name(),
                    "findings", safe(review.getFindings()),
                    "conclusion", safe(review.getConclusion()),
                    "recommendation", safe(review.getRecommendation())
            ));
        }
        List<AnalysisCorrectionEntity> acceptedCorrections = corrections.selectList(new LambdaQueryWrapper<AnalysisCorrectionEntity>()
                .eq(AnalysisCorrectionEntity::getResultId, result.getId()));
        context.put("acceptedCorrectionCount", acceptedCorrections == null ? 0 : acceptedCorrections.size());
        return context;
    }

    private Map<String, Object> validatedDraft(String generated) {
        JsonNode node;
        try {
            node = json.readTree(generated);
        } catch (Exception exception) {
            throw new LlmException("LLM draft must be valid JSON", exception);
        }
        String findings = requiredText(node, "findings");
        String conclusion = requiredText(node, "conclusion");
        String recommendation = requiredText(node, "recommendation");
        safetyPolicy.requireSafe(PromptScenario.REPORT_DRAFT_GENERATION, findings, conclusion, recommendation);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("findings", findings);
        draft.put("conclusion", conclusion);
        draft.put("recommendation", recommendation);
        draft.put("explanation", requiredText(node, "explanation"));
        draft.put("disclaimer", safetyPolicy.disclaimer(PromptScenario.REPORT_DRAFT_GENERATION));
        return draft;
    }

    private String requiredText(JsonNode node, String field) {
        return safetyPolicy.requireText(node.path(field).asText(""), TEXT_LIMIT,
                "LLM draft is missing field: " + field);
    }

    private JsonNode readTree(String value) {
        if (value == null || value.isBlank()) {
            return json.createObjectNode();
        }
        try {
            //json.readTree(value) 解析 JSON 字符串为 JsonNode 对象
            return json.readTree(value);
        } catch (Exception exception) {
            return json.createObjectNode();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
