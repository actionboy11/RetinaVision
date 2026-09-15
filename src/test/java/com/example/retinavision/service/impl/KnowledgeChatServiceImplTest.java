package com.example.retinavision.service.impl;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.mapper.KnowledgeChatMessageMapper;
import com.example.retinavision.mapper.KnowledgeChatSessionMapper;
import com.example.retinavision.pojo.DTO.KnowledgeChatRequestDTO;
import com.example.retinavision.pojo.Entity.KnowledgeChatMessageEntity;
import com.example.retinavision.pojo.Entity.KnowledgeChatSessionEntity;
import com.example.retinavision.pojo.VO.KnowledgeChatResponseVO;
import com.example.retinavision.rag.EmbeddingClient;
import com.example.retinavision.rag.QdrantClient;
import com.example.retinavision.rag.QdrantSearchHit;
import com.example.retinavision.rag.RagGroundingValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeChatServiceImplTest {

    private final KnowledgeChatSessionMapper sessions = mock(KnowledgeChatSessionMapper.class);
    private final KnowledgeChatMessageMapper messages = mock(KnowledgeChatMessageMapper.class);
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final QdrantClient qdrant = mock(QdrantClient.class);

    @Test
    void answersWithCitationsAndFixedDisclaimer() {
        when(embeddings.embed("血管面积比是什么意思")).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrant.search(any(), eq(5))).thenReturn(List.of(
                new QdrantSearchHit("p1", 0.92, 1L, 2L, "眼底指标说明", "院内规范", "血管面积比是辅助分析指标")
        ));
        LlmOrchestrationService llm = (templateCode, user) -> {
            assertThat(templateCode).isEqualTo("RAG_KNOWLEDGE_CHAT");
            assertThat(user).contains("血管面积比是辅助分析指标").contains("血管面积比是什么意思");
            return generated("{\"answer\":\"血管面积比可作为视网膜血管分割结果的辅助理解指标。\","
                    + "\"evidence\":[{\"chunkId\":2,\"quote\":\"血管面积比是辅助分析指标\"}]}");
        };

        KnowledgeChatServiceImpl service = service(llm);

        KnowledgeChatResponseVO response = service.chat(new KnowledgeChatRequestDTO(null, "血管面积比是什么意思"), 7);

        assertThat(response.answer()).contains("辅助理解指标");
        assertThat(response.disclaimer())
                .isEqualTo("医疗知识助手仅供资料检索和理解参考，不构成诊断、治疗建议或报告签发依据。");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).documentTitle()).isEqualTo("眼底指标说明");
        assertThat(response.citations().get(0).snippet()).isEqualTo("血管面积比是辅助分析指标");
        verify(sessions).insert(any(KnowledgeChatSessionEntity.class));
        verify(messages, times(2)).insert(any(KnowledgeChatMessageEntity.class));
    }

    @Test
    void emptyRetrievalDoesNotCallLlmOrInventAnswer() {
        when(embeddings.embed("如何确诊糖网")).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrant.search(any(), eq(5))).thenReturn(List.of());
        LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
        KnowledgeChatServiceImpl service = service(llm);

        KnowledgeChatResponseVO response = service.chat(new KnowledgeChatRequestDTO(null, "如何确诊糖网"), 7);

        assertThat(response.answer()).contains("知识库未检索到足够依据");
        assertThat(response.citations()).isEmpty();
        verify(llm, never()).generateJson(any(), any());
    }

    @Test
    void unsafeDiagnosticAnswerIsRejected() {
        when(embeddings.embed("我是不是患病")).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrant.search(any(), eq(5))).thenReturn(List.of(
                new QdrantSearchHit("p1", 0.92, 1L, 2L, "资料", "来源", "仅供参考")
        ));
        LlmOrchestrationService llm = (templateCode, user) -> generated(
                "{\"answer\":\"可以确诊糖尿病视网膜病变，无需复查。\"}");
        KnowledgeChatServiceImpl service = service(llm);

        assertThatThrownBy(() -> service.chat(new KnowledgeChatRequestDTO(null, "我是不是患病"), 7))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("不合规诊断措辞");
    }

    @Test
    void unverifiedModelEvidenceReturnsInsufficientAnswerWithoutCitations() {
        when(embeddings.embed("如何理解结果")).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrant.search(any(), eq(5))).thenReturn(List.of(
                new QdrantSearchHit("p1", 0.92, 1L, 2L, "资料", "来源", "仅供参考的指标说明")));
        KnowledgeChatServiceImpl service = service((templateCode, user) ->
                generated("{\"answer\":\"模型编造的解释。\",\"evidence\":[{\"chunkId\":2,\"quote\":\"不存在的原文引句\"}]}"));

        KnowledgeChatResponseVO response = service.chat(new KnowledgeChatRequestDTO(null, "如何理解结果"), 7);

        assertThat(response.answer()).contains("知识库未检索到足够依据");
        assertThat(response.citations()).isEmpty();
    }

    private KnowledgeChatServiceImpl service(LlmOrchestrationService llm) {
        return new KnowledgeChatServiceImpl(sessions, messages, embeddings, qdrant, llm,
                new LlmSafetyPolicy(), new RagGroundingValidator(new ObjectMapper(), new LlmSafetyPolicy()),
                "qwen", "qwen-plus");
    }

    private LlmGenerationResult generated(String content) {
        return new LlmGenerationResult(content, "RAG_KNOWLEDGE_CHAT", 1, "qwen", "qwen-plus", 10);
    }
}
