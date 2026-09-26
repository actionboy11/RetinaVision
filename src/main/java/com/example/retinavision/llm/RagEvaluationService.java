package com.example.retinavision.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.PromptEvaluationRunMapper;
import com.example.retinavision.mapper.PromptTemplateVersionMapper;
import com.example.retinavision.pojo.Entity.PromptEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.rag.EmbeddingClient;
import com.example.retinavision.rag.EmbeddingProperties;
import com.example.retinavision.rag.KnowledgeDocumentRetriever;
import com.example.retinavision.rag.QdrantClient;
import com.example.retinavision.rag.QdrantProperties;
import com.example.retinavision.rag.QdrantPoint;
import com.example.retinavision.rag.QdrantSearchHit;
import com.example.retinavision.rag.RagEvaluationScorer;
import com.example.retinavision.rag.RagGroundingValidator;
import com.example.retinavision.rag.RagDocumentSupport;
import com.example.retinavision.service.PromptTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.ai.rag.Query;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class RagEvaluationService {
    public static final String TEMPLATE_CODE = "RAG_KNOWLEDGE_CHAT";
    public static final String SAMPLE_VERSION = "rag-v1";
    private static final String SAMPLE_PATH = "prompt-evaluation/rag-v1.json";

    private final PromptEvaluationRunMapper runs;
    private final PromptTemplateVersionMapper versions;
    private final PromptTemplateService templates;
    private final EmbeddingClient embeddings;
    private final EmbeddingProperties embeddingProperties;
    private final QdrantClient qdrant;
    private final QdrantProperties qdrantProperties;
    private final KnowledgeDocumentRetriever retriever;
    private final LlmClient client;
    private final PromptRenderService renderer;
    private final RagGroundingValidator validator;
    private final RagEvaluationScorer scorer;
    private final LlmProperties llmProperties;
    private final ObjectMapper json;
    private final Executor executor;

    public RagEvaluationService(PromptEvaluationRunMapper runs, PromptTemplateVersionMapper versions,
                                PromptTemplateService templates, EmbeddingClient embeddings,
                                EmbeddingProperties embeddingProperties, QdrantClient qdrant,
                                QdrantProperties qdrantProperties,
                                KnowledgeDocumentRetriever retriever,
                                LlmClient client, PromptRenderService renderer, RagGroundingValidator validator,
                                RagEvaluationScorer scorer, LlmProperties llmProperties, ObjectMapper json,
                                @Qualifier("promptEvaluationExecutor") Executor executor) {
        this.runs = runs;
        this.versions = versions;
        this.templates = templates;
        this.embeddings = embeddings;
        this.embeddingProperties = embeddingProperties;
        this.qdrant = qdrant;
        this.qdrantProperties = qdrantProperties;
        this.retriever = retriever;
        this.client = client;
        this.renderer = renderer;
        this.validator = validator;
        this.scorer = scorer;
        this.llmProperties = llmProperties;
        this.json = json;
        this.executor = executor;
    }

    public PromptEvaluationRunEntity start(Long candidateVersionId, Integer userId) {
        if (candidateVersionId == null || !llmProperties.isEnabled() || !embeddingProperties.isEnabled()) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE,
                    "请先配置大模型、Embedding 并选择候选 RAG Prompt 版本");
        }
        PromptTemplateVersionEntity baseline = templates.requireActiveVersion(TEMPLATE_CODE);
        PromptTemplateVersionEntity candidate = versions.selectById(candidateVersionId);
        if (candidate == null || !baseline.getTemplateId().equals(candidate.getTemplateId())
                || baseline.getId().equals(candidateVersionId)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "候选版本无效或与当前版本相同");
        }
        loadSample();
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setTemplateCode(TEMPLATE_CODE);
        run.setBaselineVersionId(baseline.getId());
        run.setCandidateVersionId(candidate.getId());
        run.setSampleVersion(SAMPLE_VERSION);
        run.setProvider(llmProperties.getProvider());
        run.setModel(llmProperties.getModel());
        run.setEmbeddingModel(embeddingProperties.getModel());
        run.setScoreThreshold(qdrantProperties.getScoreThreshold());
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
                .orderByDesc(PromptEvaluationRunEntity::getId).last("LIMIT 50"));
    }

    public PromptEvaluationRunEntity requireRun(Long id) {
        PromptEvaluationRunEntity run = runs.selectById(id);
        if (run == null || !TEMPLATE_CODE.equals(run.getTemplateCode())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "RAG 评测记录不存在");
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
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "检索或引用自动校验未通过，不能批准发布");
        }
        if (run.getReviewDecision() != null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "该评测已经复核，请重新运行评测");
        }
        LocalDateTime reviewedAt = LocalDateTime.now();
        String decision = approved ? "APPROVED" : "REJECTED";
        if (runs.saveReviewIfPending(id, decision, score, note == null ? "" : note.trim(), doctorId, reviewedAt) != 1) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "该评测已经复核，请重新运行评测");
        }
        run.setReviewDecision(decision);
        run.setReviewScore(score);
        run.setReviewNote(note == null ? "" : note.trim());
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
            if (!embeddingProperties.getModel().equals(run.getEmbeddingModel())
                    || !Double.valueOf(qdrantProperties.getScoreThreshold()).equals(run.getScoreThreshold())) {
                throw new IllegalStateException("RAG evaluation configuration changed before execution");
            }
            JsonNode sample = loadSample();
            String collection = sample.path("collection").asText();
            PromptTemplateVersionEntity baseline = versions.selectById(run.getBaselineVersionId());
            PromptTemplateVersionEntity candidate = versions.selectById(run.getCandidateVersionId());
            if (baseline == null || candidate == null) {
                throw new IllegalStateException("Missing prompt version");
            }
            qdrant.ensureCollection(collection, embeddingProperties.getDimension());
            List<QdrantPoint> points = new ArrayList<>();
            Map<Long, QdrantSearchHit> corpus = new LinkedHashMap<>();
            for (JsonNode chunk : sample.path("chunks")) {
                long id = chunk.path("id").asLong();
                String text = chunk.path("text").asText();
                String title = chunk.path("title").asText();
                float[] vector = embeddings.embed(text);
                String pointId = UUID.nameUUIDFromBytes((SAMPLE_VERSION + ":" + id)
                        .getBytes(StandardCharsets.UTF_8)).toString();
                points.add(new QdrantPoint(pointId, vector, id, id, title, "固定合成评测集", "FAQ", "ACTIVE", text));
                corpus.put(id, new QdrantSearchHit(pointId, 1.0, id, id, title, "固定合成评测集", text));
            }
            qdrant.upsert(collection, points);
            List<Map<String, Object>> cases = new ArrayList<>();
            int hits = 0;
            double reciprocalRank = 0;
            boolean generationPassed = true;
            for (JsonNode testCase : sample.path("cases")) {
                String question = testCase.path("question").asText();
                long expectedId = testCase.path("expectedChunkId").asLong();
                List<QdrantSearchHit> retrieved = retriever.retrieve(collection, new Query(question), 3).stream()
                        .map(RagDocumentSupport::toHit).toList();
                int rank = scorer.rank(expectedId, retrieved);
                if (rank > 0) hits++;
                reciprocalRank += scorer.reciprocalRank(expectedId, retrieved);
                List<QdrantSearchHit> fixedContext = List.of(corpus.get(expectedId));
                Map<String, Object> baselineResult = generate(baseline, question, fixedContext);
                Map<String, Object> candidateResult = generate(candidate, question, fixedContext);
                generationPassed &= Boolean.TRUE.equals(candidateResult.get("passed"));
                cases.add(Map.of("caseId", testCase.path("id").asText(),
                        "title", testCase.path("title").asText(), "question", question,
                        "expectedChunkId", expectedId, "retrievedChunkIds",
                        retrieved.stream().map(QdrantSearchHit::chunkId).toList(), "rank", rank,
                        "baseline", baselineResult, "candidate", candidateResult));
            }
            int count = cases.size();
            double hitAt3 = (double) hits / count;
            run.setResultJson(json.writeValueAsString(Map.of("cases", cases,
                    "retrieval", Map.of("hitAt3", hitAt3, "mrr", reciprocalRank / count,
                            "passed", hitAt3 >= 0.75), "generationPassed", generationPassed)));
            run.setAutomatedPass(hitAt3 >= 0.75 && generationPassed);
            run.setStatus("COMPLETED");
        } catch (Exception exception) {
            run.setStatus("FAILED");
            run.setFailureReason("RAG 评测失败，请检查 Embedding、Qdrant、LLM 及评测样本配置");
        }
        run.setCompletedAt(LocalDateTime.now());
        runs.updateById(run);
    }

    private Map<String, Object> generate(PromptTemplateVersionEntity version, String question,
                                         List<QdrantSearchHit> contexts) {
        long started = System.nanoTime();
        try {
            String user = json.writeValueAsString(Map.of("question", question, "contexts",
                    contexts.stream().map(hit -> Map.of("documentId", hit.documentId(),
                            "chunkId", hit.chunkId(), "title", hit.documentTitle(),
                            "source", hit.source(), "text", hit.text())).toList()));
            RenderedPrompt prompt = renderer.render(version, user);
            RagGroundingValidator.Validation result = validator.validate(
                    client.generateJson(prompt.systemPrompt(), prompt.userPrompt()), contexts);
            return Map.of("passed", result.valid(), "answer", result.answer(),
                    "citations", result.citations(), "latencyMs", (System.nanoTime() - started) / 1_000_000,
                    "errorCode", result.valid() ? "NONE" : "UNVERIFIED_EVIDENCE");
        } catch (Exception exception) {
            return Map.of("passed", false, "answer", "", "citations", List.of(),
                    "latencyMs", (System.nanoTime() - started) / 1_000_000, "errorCode", "LLM_ERROR");
        }
    }

    private JsonNode loadSample() {
        try (InputStream input = new ClassPathResource(SAMPLE_PATH).getInputStream()) {
            JsonNode sample = json.readTree(input);
            if (!SAMPLE_VERSION.equals(sample.path("version").asText())
                    || !"retina_rag_eval_v1".equals(sample.path("collection").asText())
                    || !sample.path("chunks").isArray() || sample.path("chunks").isEmpty()
                    || !sample.path("cases").isArray() || sample.path("cases").isEmpty()) {
                throw new IllegalStateException("Invalid RAG evaluation corpus");
            }
            return sample;
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "RAG 评测样本不可用");
        }
    }
}
