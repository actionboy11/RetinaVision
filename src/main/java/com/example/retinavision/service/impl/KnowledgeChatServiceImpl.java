package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.llm.PromptScenario;
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
import com.example.retinavision.service.KnowledgeChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeChatServiceImpl implements KnowledgeChatService {
    private static final String TEMPLATE_CODE = "RAG_KNOWLEDGE_CHAT";
    private static final Logger log = LoggerFactory.getLogger(KnowledgeChatServiceImpl.class);
    private static final String INSUFFICIENT_ANSWER = "知识库未检索到足够依据，请补充资料或换一种问法。";

    private final KnowledgeChatSessionMapper sessions;
    private final KnowledgeChatMessageMapper messages;
    private final EmbeddingClient embeddings;
    private final QdrantClient qdrant;
    private final LlmOrchestrationService llm;
    private final LlmSafetyPolicy safetyPolicy;
    private final RagGroundingValidator groundingValidator;
    private final String llmProvider;
    private final String llmModel;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public KnowledgeChatServiceImpl(KnowledgeChatSessionMapper sessions,
                                    KnowledgeChatMessageMapper messages,
                                    EmbeddingClient embeddings,
                                    QdrantClient qdrant,
                                    LlmOrchestrationService llm,
                                    LlmSafetyPolicy safetyPolicy,
                                    RagGroundingValidator groundingValidator,
                                    com.example.retinavision.llm.LlmProperties llmProperties) {
        this(sessions, messages, embeddings, qdrant, llm, safetyPolicy, groundingValidator,
                llmProperties.getProvider(), llmProperties.getModel());
    }

    KnowledgeChatServiceImpl(KnowledgeChatSessionMapper sessions,
                             KnowledgeChatMessageMapper messages,
                             EmbeddingClient embeddings,
                             QdrantClient qdrant,
                             LlmOrchestrationService llm,
                             LlmSafetyPolicy safetyPolicy,
                             RagGroundingValidator groundingValidator,
                             String llmProvider,
                             String llmModel) {
        this.sessions = sessions;
        this.messages = messages;
        this.embeddings = embeddings;
        this.qdrant = qdrant;
        this.llm = llm;
        this.safetyPolicy = safetyPolicy;
        this.groundingValidator = groundingValidator;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
    }

    @Override
    public KnowledgeChatSessionEntity createSession(Integer userId) {
        KnowledgeChatSessionEntity session = new KnowledgeChatSessionEntity();
        LocalDateTime now = LocalDateTime.now();
        session.setUserId(userId);
        session.setTitle("新的知识咨询");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessions.insert(session);
        return session;
    }

    @Override
    public List<KnowledgeChatSessionEntity> listSessions(Integer userId) {
        return sessions.selectList(new LambdaQueryWrapper<KnowledgeChatSessionEntity>()
                .eq(KnowledgeChatSessionEntity::getUserId, userId)
                .orderByDesc(KnowledgeChatSessionEntity::getUpdatedAt));
    }

    @Override
    public List<KnowledgeChatMessageEntity> listMessages(Long sessionId, Integer userId) {
        requireOwnedSession(sessionId, userId);
        return messages.selectList(new LambdaQueryWrapper<KnowledgeChatMessageEntity>()
                .eq(KnowledgeChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(KnowledgeChatMessageEntity::getCreatedAt));
    }

    @Override
    @Transactional
    public KnowledgeChatResponseVO chat(KnowledgeChatRequestDTO request, Integer userId) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "问题不能为空");
        }
        KnowledgeChatSessionEntity session = request.sessionId() == null
                ? createSession(userId)
                : requireOwnedSession(request.sessionId(), userId);

        String question = request.question().trim();
        saveMessage(session.getId(), userId, "USER", question, null);

        long start = System.nanoTime();
        long embeddingStart = System.nanoTime();
        float[] vector = embeddings.embed(question);
        long searchStart = System.nanoTime();
        List<QdrantSearchHit> hits = qdrant.search(vector, 5);
        long llmStart = System.nanoTime();

        List<KnowledgeChatResponseVO.Citation> citations = List.of();
        String answer;
        if (hits.isEmpty()) {
            answer = INSUFFICIENT_ANSWER;
        } else {
            RagGroundingValidator.Validation grounded = answerFromLlm(question, hits);
            answer = grounded.valid() ? grounded.answer() : INSUFFICIENT_ANSWER;
            citations = grounded.citations();
        }
        long end = System.nanoTime();
        log.info("Knowledge chat completed sessionId={} userId={} embeddingMs={} searchMs={} llmMs={} totalMs={} hits={}",
                session.getId(), userId, millis(searchStart - embeddingStart), millis(llmStart - searchStart),
                millis(end - llmStart), millis(end - start), hits.size());

        String citationsJson = toJson(citations);
        saveMessage(session.getId(), userId, "ASSISTANT", answer, citationsJson);
        session.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
        session.setUpdatedAt(LocalDateTime.now());
        sessions.updateById(session);
        return new KnowledgeChatResponseVO(session.getId(), answer, citations,
                safetyPolicy.disclaimer(PromptScenario.RAG_KNOWLEDGE_CHAT));
    }

    private RagGroundingValidator.Validation answerFromLlm(String question, List<QdrantSearchHit> hits) {
        LlmGenerationResult generation = llm.generateJson(TEMPLATE_CODE, userPrompt(question, hits));
        return groundingValidator.validate(generation.content(), hits);
    }

    private String userPrompt(String question, List<QdrantSearchHit> hits) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        payload.put("contexts", hits.stream().map(hit -> Map.of(
                "documentId", hit.documentId(),
                "chunkId", hit.chunkId(),
                "title", hit.documentTitle(),
                "source", hit.source(),
                "text", hit.text()
        )).toList());
        try {
            return json.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVER_ERROR, "知识助手请求构造失败");
        }
    }

    private KnowledgeChatSessionEntity requireOwnedSession(Long sessionId, Integer userId) {
        KnowledgeChatSessionEntity session = sessions.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权访问该知识助手会话");
        }
        return session;
    }

    private void saveMessage(Long sessionId, Integer userId, String role, String content, String citationsJson) {
        KnowledgeChatMessageEntity message = new KnowledgeChatMessageEntity();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCitationsJson(citationsJson);
        message.setLlmProvider(llmProvider);
        message.setLlmModel(llmModel);
        message.setCreatedAt(LocalDateTime.now());
        messages.insert(message);
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private long millis(long nanos) {
        return nanos / 1_000_000;
    }
}
