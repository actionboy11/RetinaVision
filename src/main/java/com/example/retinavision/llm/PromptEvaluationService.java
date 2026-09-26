package com.example.retinavision.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.PromptEvaluationRunMapper;
import com.example.retinavision.mapper.PromptTemplateVersionMapper;
import com.example.retinavision.pojo.Entity.PromptEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.service.PromptTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class PromptEvaluationService {
    public static final String TEMPLATE_CODE = "REPORT_DRAFT_GENERATION";
    public static final String SAMPLE_VERSION = "report-draft-v1";
    private static final String SAMPLE_PATH = "prompt-evaluation/report-draft-v1.json";

    private final PromptEvaluationRunMapper runs;
    private final PromptTemplateVersionMapper versions;
    private final PromptTemplateService templates;
    private final LlmClient client;
    private final PromptRenderService renderer;
    private final ReportDraftEvaluationScorer scorer;
    private final LlmProperties properties;
    private final ObjectMapper json;
    private final Executor executor;

    public PromptEvaluationService(PromptEvaluationRunMapper runs,
                                   PromptTemplateVersionMapper versions,
                                   PromptTemplateService templates,
                                   LlmClient client,
                                   PromptRenderService renderer,
                                   ReportDraftEvaluationScorer scorer,
                                   LlmProperties properties,
                                   ObjectMapper json,
                                   @Qualifier("promptEvaluationExecutor") Executor executor) {
        this.runs = runs;
        this.versions = versions;
        this.templates = templates;
        this.client = client;
        this.renderer = renderer;
        this.scorer = scorer;
        this.properties = properties;
        this.json = json;
        this.executor = executor;
    }

    public PromptEvaluationRunEntity start(Long candidateVersionId, Integer userId) {
        if (candidateVersionId == null || !properties.isEnabled()) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "请先配置大模型并选择候选 Prompt 版本");
        }
        PromptTemplateVersionEntity baseline = templates.requireActiveVersion(TEMPLATE_CODE);
        PromptTemplateVersionEntity candidate = versions.selectById(candidateVersionId);
        if (candidate == null || !baseline.getTemplateId().equals(candidate.getTemplateId())
                || baseline.getId().equals(candidateVersionId)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "候选版本无效或与当前版本相同");
        }
        JsonNode sample = loadSample();
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setTemplateCode(TEMPLATE_CODE);
        run.setBaselineVersionId(baseline.getId());
        run.setCandidateVersionId(candidate.getId());
        run.setSampleVersion(sample.path("version").asText());
        run.setProvider(properties.getProvider());
        run.setModel(properties.getModel());
        run.setStatus("QUEUED");
        run.setCreatedBy(userId);
        run.setCreatedAt(LocalDateTime.now());
        runs.insert(run);
        try {
            executor.execute(() -> execute(run.getId()));
        } catch (RuntimeException exception) {
            run.setStatus("FAILED");
            run.setFailureReason("评测队列已满，请稍后重试");
            run.setCompletedAt(LocalDateTime.now());
            runs.updateById(run);
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, run.getFailureReason());
        }
        return run;
    }

    public List<PromptEvaluationRunEntity> list() {
        return runs.selectList(new LambdaQueryWrapper<PromptEvaluationRunEntity>()
                .eq(PromptEvaluationRunEntity::getTemplateCode, TEMPLATE_CODE)
                .orderByDesc(PromptEvaluationRunEntity::getCreatedAt)
                .last("LIMIT 50"));
    }

    public PromptEvaluationRunEntity requireRun(Long id) {
        PromptEvaluationRunEntity run = runs.selectById(id);
        if (run == null || !TEMPLATE_CODE.equals(run.getTemplateCode())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "评测记录不存在");
        }
        return run;
    }

    public PromptEvaluationRunEntity review(Long id, boolean approved, int score, String note, Integer doctorId) {
        PromptEvaluationRunEntity run = requireRun(id);
        if (!"COMPLETED".equals(run.getStatus()) || score < 1 || score > 5
                || (note != null && note.length() > 500)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "评测尚未完成或评分无效");
        }
        if (approved && !Boolean.TRUE.equals(run.getAutomatedPass())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "自动安全校验未通过，不能批准发布");
        }
        if (run.getReviewDecision() != null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "该评测已经复核，请重新运行评测");
        }
        String decision = approved ? "APPROVED" : "REJECTED";
        String safeNote = note == null ? "" : note.trim();
        LocalDateTime reviewedAt = LocalDateTime.now();
        if (runs.saveReviewIfPending(id, decision, score, safeNote, doctorId, reviewedAt) != 1) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "该评测已经复核，请重新运行评测");
        }
        run.setReviewDecision(decision);
        run.setReviewScore(score);
        run.setReviewNote(safeNote);
        run.setReviewedBy(doctorId);
        run.setReviewedAt(reviewedAt);
        return run;
    }

    void execute(Long runId) {
        PromptEvaluationRunEntity run = runs.selectById(runId);
        if (run == null || !"QUEUED".equals(run.getStatus())) {
            return;
        }
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        runs.updateById(run);
        try {
            PromptTemplateVersionEntity baseline = versions.selectById(run.getBaselineVersionId());
            PromptTemplateVersionEntity candidate = versions.selectById(run.getCandidateVersionId());
            if (baseline == null || candidate == null) {
                throw new IllegalStateException("Prompt version missing");
            }
            JsonNode sample = loadSample();
            List<Map<String, Object>> results = new ArrayList<>();
            boolean candidatePassed = true;
            for (JsonNode testCase : sample.path("cases")) {
                String context = json.writeValueAsString(testCase.path("context"));
                Map<String, Object> baselineResult = generate(baseline, context);
                Map<String, Object> candidateResult = generate(candidate, context);
                candidatePassed &= Boolean.TRUE.equals(candidateResult.get("passed"));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("caseId", testCase.path("id").asText());
                item.put("title", testCase.path("title").asText());
                item.put("context", testCase.path("context"));
                item.put("baseline", baselineResult);
                item.put("candidate", candidateResult);
                results.add(item);
            }
            run.setResultJson(json.writeValueAsString(Map.of("cases", results)));
            run.setAutomatedPass(candidatePassed);
            run.setStatus("COMPLETED");
        } catch (Exception exception) {
            run.setStatus("FAILED");
            run.setFailureReason("评测执行失败，请检查模板与模型服务配置");
        }
        run.setCompletedAt(LocalDateTime.now());
        runs.updateById(run);
    }

    private Map<String, Object> generate(PromptTemplateVersionEntity version, String context) {
        long started = System.nanoTime();
        try {
            RenderedPrompt prompt = renderer.render(version, context);
            ReportDraftEvaluationScorer.Score score = scorer.score(
                    client.generateJson(prompt.systemPrompt(), prompt.userPrompt()));
            return outcome(score.passed(), score.output(), score.errorCode(), started);
        } catch (RuntimeException exception) {
            return outcome(false, "", "LLM_ERROR", started);
        }
    }

    private Map<String, Object> outcome(boolean passed, String output, String errorCode, long started) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("passed", passed);
        item.put("output", output);
        item.put("errorCode", errorCode);
        item.put("latencyMs", (System.nanoTime() - started) / 1_000_000);
        return item;
    }

    private JsonNode loadSample() {
        try (InputStream input = new ClassPathResource(SAMPLE_PATH).getInputStream()) {
            JsonNode sample = json.readTree(input);
            if (!sample.path("cases").isArray() || sample.path("cases").isEmpty()) {
                throw new IllegalStateException("Empty evaluation corpus");
            }
            if (!SAMPLE_VERSION.equals(sample.path("version").asText())) {
                throw new IllegalStateException("Unexpected evaluation corpus version");
            }
            return sample;
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "评测样本不可用");
        }
    }
}
