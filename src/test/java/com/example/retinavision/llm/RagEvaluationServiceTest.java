package com.example.retinavision.llm;

import com.example.retinavision.mapper.PromptEvaluationRunMapper;
import com.example.retinavision.mapper.PromptTemplateVersionMapper;
import com.example.retinavision.pojo.Entity.PromptEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.rag.EmbeddingClient;
import com.example.retinavision.rag.EmbeddingProperties;
import com.example.retinavision.rag.QdrantClient;
import com.example.retinavision.rag.QdrantProperties;
import com.example.retinavision.rag.QdrantSearchHit;
import com.example.retinavision.rag.RagEvaluationScorer;
import com.example.retinavision.rag.RagGroundingValidator;
import com.example.retinavision.service.PromptTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagEvaluationServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PromptEvaluationRunMapper runs = mock(PromptEvaluationRunMapper.class);
    private final PromptTemplateVersionMapper versions = mock(PromptTemplateVersionMapper.class);
    private final PromptTemplateService templates = mock(PromptTemplateService.class);
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final QdrantClient qdrant = mock(QdrantClient.class);
    private final EmbeddingProperties embeddingProperties = new EmbeddingProperties();
    private final LlmProperties llmProperties = new LlmProperties();
    private final QdrantProperties qdrantProperties = new QdrantProperties();

    @Test
    void evaluationRecordsRetrievalAndGroundedGenerationSeparately() throws Exception {
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setId(1L);
        run.setStatus("QUEUED");
        run.setBaselineVersionId(10L);
        run.setCandidateVersionId(20L);
        run.setEmbeddingModel("text-embedding-v4");
        run.setScoreThreshold(0.2);
        when(runs.selectById(1L)).thenReturn(run);
        when(versions.selectById(10L)).thenReturn(version(10L, "baseline"));
        when(versions.selectById(20L)).thenReturn(version(20L, "candidate"));
        when(embeddings.embed(anyString())).thenReturn(new float[]{1, 0});
        when(qdrant.search(eq("retina_rag_eval_v1"), any(), eq(3))).thenReturn(List.of(
                new QdrantSearchHit("a", 0.9, 1001L, 1001L, "质量", "合成", "质量"),
                new QdrantSearchHit("b", 0.8, 1002L, 1002L, "比例", "合成", "比例"),
                new QdrantSearchHit("c", 0.7, 1004L, 1004L, "报告", "合成", "报告")));
        LlmClient client = (system, user) -> {
            try {
                JsonNode context = json.readTree(user).path("contexts").get(0);
                if (system.equals("baseline")) return "{\"answer\":\"旧版回答\"}";
                String text = context.path("text").asText();
                return json.writeValueAsString(java.util.Map.of("answer", "根据资料，这是辅助解释。",
                        "evidence", List.of(java.util.Map.of("chunkId", context.path("chunkId").asLong(),
                                "quote", text.substring(0, 12)))));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        };
        RagEvaluationService service = service(client);

        service.execute(1L);

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getAutomatedPass()).isTrue();
        JsonNode result = json.readTree(run.getResultJson());
        assertThat(result.path("retrieval").path("hitAt3").asDouble()).isEqualTo(0.75);
        assertThat(result.path("cases").get(0).path("baseline").path("passed").asBoolean()).isFalse();
        assertThat(result.path("cases").get(0).path("candidate").path("passed").asBoolean()).isTrue();
        verify(qdrant).ensureCollection("retina_rag_eval_v1", 2);
        verify(qdrant).upsert(eq("retina_rag_eval_v1"), any());
    }

    private RagEvaluationService service(LlmClient client) {
        embeddingProperties.setDimension(2);
        embeddingProperties.setEnabled(true);
        llmProperties.setEnabled(true);
        Executor executor = Runnable::run;
        return new RagEvaluationService(runs, versions, templates, embeddings, embeddingProperties,
                qdrant, qdrantProperties, client, new PromptRenderService(),
                new RagGroundingValidator(json, new LlmSafetyPolicy()), new RagEvaluationScorer(),
                llmProperties, json, executor);
    }

    private PromptTemplateVersionEntity version(Long id, String system) {
        PromptTemplateVersionEntity version = new PromptTemplateVersionEntity();
        version.setId(id);
        version.setSystemPrompt(system);
        return version;
    }
}
