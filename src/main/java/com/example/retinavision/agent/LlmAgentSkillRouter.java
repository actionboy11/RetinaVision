package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmAgentSkillRouter implements AgentSkillRouter {
    private static final double MIN_CONFIDENCE = 0.65;
    private final LlmOrchestrationService llm;
    private final ObjectMapper json;
    private final AgentSkillRegistry registry;

    public LlmAgentSkillRouter(LlmOrchestrationService llm, ObjectMapper json, AgentSkillRegistry registry) {
        this.llm = llm;
        this.json = json;
        this.registry = registry;
    }

    @Override
    public AgentSkillRoute route(String question, AgentSkillCode currentSkill) {
        throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Skill 目录尚未加载");
    }

    @Override
    public AgentSkillRoute route(String question, AgentSkillCode currentSkill,
                                 List<AgentSkillDefinition> availableSkills) {
        if (availableSkills == null || availableSkills.isEmpty()) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "当前没有可用的 Agent Skill");
        }
        String context = writeContext(question, currentSkill, availableSkills);
        String content = llm.generateJson("AGENT_SKILL_ROUTER", context).content();
        try {
            JsonNode root = json.readTree(content);
            String codeValue = root.path("skillCode").asText(null);
            AgentSkillCode code;
            try { code = AgentSkillCode.valueOf(codeValue); }
            catch (IllegalArgumentException | NullPointerException exception) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "LLM 返回了未知 Skill");
            }
            if (availableSkills.stream().noneMatch(item -> item.code() == code)) {
                throw new BaseException(ErrorMessageSignal.FORBIDDEN, "LLM 选择的 Skill 当前不可用");
            }
            double confidence = Math.max(0, Math.min(1, root.path("confidence").asDouble(0)));
            if (confidence < MIN_CONFIDENCE) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请说明您要查询任务、临床待办还是医学知识");
            }
            Map<String, String> arguments = new LinkedHashMap<>();
            JsonNode argumentNode = root.path("arguments");
            if (!argumentNode.isMissingNode() && !argumentNode.isObject()) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 路由参数格式无效");
            }
            argumentNode.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isValueNode()) {
                    throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 路由参数格式无效");
                }
                arguments.put(entry.getKey(), entry.getValue().asText());
            });
            Map<String, String> normalized = registry.validateAndNormalize(code, UserRole.DOCTOR, arguments);
            return new AgentSkillRoute(code, confidence, normalized);
        } catch (BaseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 路由响应格式无效");
        }
    }

    private String writeContext(String question, AgentSkillCode currentSkill,
                                List<AgentSkillDefinition> definitions) {
        List<Map<String, Object>> skillItems = new ArrayList<>();
        for (AgentSkillDefinition definition : definitions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skillCode", definition.code().name());
            item.put("name", definition.name());
            item.put("description", definition.description());
            item.put("version", definition.version());
            item.put("routingExamples", readJson(definition.routingExamplesJson()));
            item.put("workflow", definition.workflowPrompt());
            item.put("arguments", registry.argumentSchema(definition.code()));
            skillItems.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question == null ? "" : question.trim());
        if (currentSkill != null) payload.put("currentSkill", currentSkill.name());
        payload.put("skills", skillItems);
        try { return json.writeValueAsString(payload); }
        catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVER_ERROR, "无法构造 Skill 路由上下文");
        }
    }

    private Object readJson(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return json.readTree(value); }
        catch (Exception ignored) { return List.of(); }
    }
}
