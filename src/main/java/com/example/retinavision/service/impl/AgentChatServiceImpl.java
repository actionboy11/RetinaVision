package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.agent.AgentConversationMessage;
import com.example.retinavision.agent.AgentModelGateway;
import com.example.retinavision.agent.AgentToolBundle;
import com.example.retinavision.agent.AgentToolFactory;
import com.example.retinavision.agent.AgentSkillCode;
import com.example.retinavision.agent.AgentSkillRuntimeVersion;
import com.example.retinavision.agent.AgentSkillVersionBindingService;
import com.example.retinavision.agent.DoctorAgentSkillOrchestrator;
import com.example.retinavision.agent.DoctorAgentSkillResult;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.llm.PromptRenderService;
import com.example.retinavision.llm.PromptScenario;
import com.example.retinavision.mapper.AgentChatMessageMapper;
import com.example.retinavision.mapper.AgentChatSessionMapper;
import com.example.retinavision.mapper.AgentSkillExecutionLogMapper;
import com.example.retinavision.pojo.DTO.AgentChatRequestDTO;
import com.example.retinavision.pojo.Entity.AgentChatMessageEntity;
import com.example.retinavision.pojo.Entity.AgentChatSessionEntity;
import com.example.retinavision.pojo.Entity.AgentSkillExecutionLogEntity;
import com.example.retinavision.pojo.Entity.LlmCallLogEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.pojo.VO.AgentChatResponseVO;
import com.example.retinavision.pojo.VO.AgentSkillSummaryVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.service.AgentChatService;
import com.example.retinavision.service.LlmCallLogService;
import com.example.retinavision.service.PromptTemplateService;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;

@Service
public class AgentChatServiceImpl implements AgentChatService {
    private static final String CLINICAL_TEMPLATE_CODE = "CLINICAL_ASSISTANT_AGENT";
    private static final String PATIENT_TEMPLATE_CODE = "PATIENT_ASSISTANT_AGENT";
    private final AgentChatSessionMapper sessions;
    private final AgentChatMessageMapper messages;
    private final PromptTemplateService prompts;
    private final PromptRenderService renderer;
    private final AgentModelGateway gateway;
    private final AgentToolFactory toolFactory;
    private final LlmSafetyPolicy safetyPolicy;
    private final LlmProperties llmProperties;
    private final LlmCallLogService callLogs;
    private final DoctorAgentSkillOrchestrator skillOrchestrator;
    private final ObjectMapper json;
    private final AgentSkillExecutionLogMapper skillLogs;
    private final AgentSkillVersionBindingService skillVersions;

    public AgentChatServiceImpl(AgentChatSessionMapper sessions, AgentChatMessageMapper messages,
                                PromptTemplateService prompts, PromptRenderService renderer,
                                AgentModelGateway gateway, AgentToolFactory toolFactory,
                                LlmSafetyPolicy safetyPolicy, LlmProperties llmProperties,
                                LlmCallLogService callLogs,
                                DoctorAgentSkillOrchestrator skillOrchestrator,
                                ObjectMapper json,
                                AgentSkillExecutionLogMapper skillLogs,
                                AgentSkillVersionBindingService skillVersions) {
        this.sessions = sessions;
        this.messages = messages;
        this.prompts = prompts;
        this.renderer = renderer;
        this.gateway = gateway;
        this.toolFactory = toolFactory;
        this.safetyPolicy = safetyPolicy;
        this.llmProperties = llmProperties;
        this.callLogs = callLogs;
        this.skillOrchestrator = skillOrchestrator;
        this.json = json;
        this.skillLogs = skillLogs;
        this.skillVersions = skillVersions;
    }

    @Override
    public AgentChatSessionEntity createSession(CurrentUserVO user) {
        requireRole(user);
        AgentChatSessionEntity session = new AgentChatSessionEntity();
        LocalDateTime now = LocalDateTime.now();
        session.setUserId(user.getId());
        session.setTitle("新的智能助手会话");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessions.insert(session);
        return session;
    }

    @Override
    public List<AgentChatSessionEntity> listSessions(CurrentUserVO user) {
        requireRole(user);
        return sessions.selectList(new LambdaQueryWrapper<AgentChatSessionEntity>()
                .eq(AgentChatSessionEntity::getUserId, user.getId())
                .orderByDesc(AgentChatSessionEntity::getUpdatedAt));
    }

    @Override
    public List<AgentChatMessageEntity> listMessages(Long sessionId, CurrentUserVO user) {
        requireOwnedSession(sessionId, user);
        return messages.selectList(new LambdaQueryWrapper<AgentChatMessageEntity>()
                .eq(AgentChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(AgentChatMessageEntity::getCreatedAt));
    }

    @Override
    public AgentChatResponseVO chat(AgentChatRequestDTO request, CurrentUserVO user) {
        requireRole(user);
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "问题不能为空");
        }
        AgentChatSessionEntity session = request.sessionId() == null
                ? createSession(user) : requireOwnedSession(request.sessionId(), user);
        String question = request.question().trim();
        List<AgentConversationMessage> history = recentHistory(session.getId());
        saveMessage(session.getId(), user.getId(), "USER", question, null);
        history.add(new AgentConversationMessage("USER", question));

        long skillStartedAt = System.nanoTime();
        java.util.Optional<DoctorAgentSkillResult> skillResult;
        try {
            skillResult = skillOrchestrator.handle(session.getId(), question, user);
        } catch (RuntimeException exception) {
            recordSkillFailure(session.getId(), user.getId(), elapsedMillis(skillStartedAt), exception);
            throw exception;
        }
        if (skillResult.isPresent()) {
            return completeStructuredSkill(session, user, question, skillResult.get(), elapsedMillis(skillStartedAt));
        }

        PromptScenario scenario = scenario(user);
        String templateCode = scenario == PromptScenario.PATIENT_ASSISTANT_AGENT
                ? PATIENT_TEMPLATE_CODE : CLINICAL_TEMPLATE_CODE;
        PromptTemplateVersionEntity version = prompts.requireActiveVersion(templateCode);
        String systemPrompt = renderer.render(version, "{}").systemPrompt();
        String traceId = UUID.randomUUID().toString();
        AgentToolBundle bundle = user.getRoleCode() == UserRole.DOCTOR
                ? toolFactory.create(session.getId(), user, traceId, Set.of("searchMedicalKnowledge"))
                : toolFactory.create(session.getId(), user, traceId);
        AgentSkillRuntimeVersion knowledgeVersion = user.getRoleCode() == UserRole.DOCTOR
                ? skillVersions.resolve(session.getId(), AgentSkillCode.MEDICAL_KNOWLEDGE_QA) : null;
        long startedAt = System.nanoTime();
        String answer;
        try {
            answer = gateway.generate(systemPrompt, history, bundle.callbacks());
            safetyPolicy.requireSafe(scenario, answer);
            answer = safetyPolicy.requireText(answer, 3000, "智能助手未返回有效内容");
            callLogs.record(callLog(version, scenario, templateCode, true, elapsedMillis(startedAt), null));
            if (knowledgeVersion != null) {
                recordSkillExecution(session.getId(), user.getId(), AgentSkillCode.MEDICAL_KNOWLEDGE_QA.name(),
                        knowledgeVersion.version(), 0.55, true, elapsedMillis(startedAt), null);
            }
        } catch (RuntimeException exception) {
            callLogs.record(callLog(version, scenario, templateCode, false, elapsedMillis(startedAt),
                    exception.getClass().getSimpleName() + ": 智能助手调用失败"));
            if (knowledgeVersion != null) {
                recordSkillExecution(session.getId(), user.getId(), AgentSkillCode.MEDICAL_KNOWLEDGE_QA.name(),
                        knowledgeVersion.version(), 0.55, false, elapsedMillis(startedAt),
                        exception.getClass().getSimpleName());
            }
            throw exception;
        }

        saveMessage(session.getId(), user.getId(), "ASSISTANT", answer, null);
        session.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
        session.setUpdatedAt(LocalDateTime.now());
        sessions.updateById(session);
        AgentSkillSummaryVO skill = knowledgeVersion == null ? null
                : new AgentSkillSummaryVO(AgentSkillCode.MEDICAL_KNOWLEDGE_QA.name(), "医学知识检索",
                knowledgeVersion.version());
        return new AgentChatResponseVO(session.getId(), answer, skill, null, null, List.of(),
                bundle.summaries(), bundle.citations(), safetyPolicy.disclaimer(scenario));
    }

    private List<AgentConversationMessage> recentHistory(Long sessionId) {
        if (sessionId == null) {
            return new ArrayList<>();
        }
        List<AgentChatMessageEntity> stored = messages.selectList(
                new LambdaQueryWrapper<AgentChatMessageEntity>()
                        .eq(AgentChatMessageEntity::getSessionId, sessionId)
                        .orderByDesc(AgentChatMessageEntity::getId)
                        .last("LIMIT 10"));
        List<AgentConversationMessage> result = new ArrayList<>();
        for (int index = stored.size() - 1; index >= 0; index--) {
            AgentChatMessageEntity message = stored.get(index);
            result.add(new AgentConversationMessage(message.getRole(), message.getContent()));
        }
        return result;
    }

    private AgentChatSessionEntity requireOwnedSession(Long sessionId, CurrentUserVO user) {
        requireRole(user);
        AgentChatSessionEntity session = sessions.selectById(sessionId);
        if (session == null || !user.getId().equals(session.getUserId())) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权访问该智能助手会话");
        }
        return session;
    }

    private AgentChatResponseVO completeStructuredSkill(AgentChatSessionEntity session, CurrentUserVO user,
                                                        String question, DoctorAgentSkillResult result,
                                                        long latencyMs) {
        PromptScenario scenario = scenario(user);
        safetyPolicy.requireSafe(scenario, result.answer());
        AgentChatResponseVO response = new AgentChatResponseVO(session.getId(), result.answer(),
                new AgentSkillSummaryVO(result.skillCode().name(), skillName(result.skillCode().name()),
                        result.skillVersion()),
                result.data(), result.pagination(), result.actions(), List.of(), List.of(),
                safetyPolicy.disclaimer(scenario));
        saveMessage(session.getId(), user.getId(), "ASSISTANT", result.answer(), write(response));
        session.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
        session.setUpdatedAt(LocalDateTime.now());
        sessions.updateById(session);
        AgentSkillExecutionLogEntity log = new AgentSkillExecutionLogEntity();
        log.setSessionId(session.getId()); log.setUserId(user.getId()); log.setTraceId(UUID.randomUUID().toString());
        log.setSkillCode(result.skillCode().name()); log.setSkillVersion(result.skillVersion());
        log.setConfidence(result.confidence());
        log.setSuccess(true); log.setLatencyMs(latencyMs); log.setCreatedAt(LocalDateTime.now());
        skillLogs.insert(log);
        return response;
    }

    private void saveMessage(Long sessionId, Integer userId, String role, String content,
                             String structuredContentJson) {
        AgentChatMessageEntity message = new AgentChatMessageEntity();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setLlmProvider(llmProperties.getProvider());
        message.setLlmModel(llmProperties.getModel());
        message.setStructuredContentJson(structuredContentJson);
        message.setCreatedAt(LocalDateTime.now());
        messages.insert(message);
    }

    private void recordSkillFailure(Long sessionId, Integer userId, long latencyMs, RuntimeException exception) {
        recordSkillExecution(sessionId, userId, "UNRESOLVED", 0, null, false, latencyMs,
                exception.getClass().getSimpleName());
    }

    private void recordSkillExecution(Long sessionId, Integer userId, String skillCode, int skillVersion,
                                      Double confidence, boolean success, long latencyMs, String errorType) {
        AgentSkillExecutionLogEntity log = new AgentSkillExecutionLogEntity();
        log.setSessionId(sessionId);
        log.setUserId(userId);
        log.setTraceId(UUID.randomUUID().toString());
        log.setSkillCode(skillCode);
        log.setSkillVersion(skillVersion);
        log.setConfidence(confidence);
        log.setSuccess(success);
        log.setLatencyMs(latencyMs);
        log.setErrorType(errorType);
        log.setCreatedAt(LocalDateTime.now());
        skillLogs.insert(log);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "智能助手结构化结果保存失败");
        }
    }

    private String skillName(String code) {
        return switch (code) {
            case "DOCTOR_WORKLOAD_OVERVIEW" -> "医生工作量总览";
            case "ASSIGNED_CASE_SEARCH" -> "负责病例筛选";
            case "CASE_CLINICAL_SUMMARY" -> "病例临床摘要";
            case "CASE_FOLLOWUP_ANALYSIS" -> "病例随访比较";
            default -> "医学知识检索";
        };
    }

    private void requireRole(CurrentUserVO user) {
        if (user == null || (user.getRoleCode() != UserRole.USER
                && user.getRoleCode() != UserRole.DOCTOR && user.getRoleCode() != UserRole.ADMIN)) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "智能助手仅限患者、医生或管理员使用");
        }
    }

    private PromptScenario scenario(CurrentUserVO user) {
        return user.getRoleCode() == UserRole.USER
                ? PromptScenario.PATIENT_ASSISTANT_AGENT : PromptScenario.CLINICAL_ASSISTANT_AGENT;
    }

    private LlmCallLogEntity callLog(PromptTemplateVersionEntity version, PromptScenario scenario,
                                     String templateCode, boolean success,
                                     long latencyMs, String errorSummary) {
        LlmCallLogEntity log = new LlmCallLogEntity();
        log.setScenario(scenario.name());
        log.setTemplateCode(templateCode);
        log.setTemplateVersion(version.getVersion());
        log.setProvider(llmProperties.getProvider());
        log.setModel(llmProperties.getModel());
        log.setSuccess(success);
        log.setLatencyMs(latencyMs);
        log.setErrorSummary(errorSummary);
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
