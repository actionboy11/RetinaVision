package com.example.retinavision.agent.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.agent.*;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmException;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.mapper.AgentEvaluationRunBindingMapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultAgentEvaluationCaseExecutor implements AgentEvaluationCaseExecutor {
    private final AgentEvaluationRunBindingMapper bindings;
    private final AgentSkillVersionMapper skillVersions;
    private final AgentSkillMapper skills;
    private final LlmOrchestrationService llm;
    private final AgentSkillRegistry registry;
    private final ObjectMapper json;
    private final Map<Long, RunConfiguration> configurations = new ConcurrentHashMap<>();
    private final Map<Long, AgentEvaluationFixtureRuntime> runtimes = new ConcurrentHashMap<>();

    public DefaultAgentEvaluationCaseExecutor(AgentEvaluationRunBindingMapper bindings,
                                              AgentSkillVersionMapper skillVersions,
                                              AgentSkillMapper skills,
                                              LlmOrchestrationService llm,
                                              AgentSkillRegistry registry,
                                              ObjectMapper json) {
        this.bindings = bindings;
        this.skillVersions = skillVersions;
        this.skills = skills;
        this.llm = llm;
        this.registry = registry;
        this.json = json;
    }

    @Override
    public AgentEvaluationCaseOutcome execute(AgentEvaluationRunEntity run,
                                              AgentEvaluationCaseEntity testCase) {
        long startedAt = System.nanoTime();
        RunConfiguration configuration = configurations.computeIfAbsent(run.getId(), this::loadConfiguration);
        AgentEvaluationFixtureRuntime runtime = runtimes.computeIfAbsent(run.getId(), ignored ->
                new AgentEvaluationFixtureRuntime(router(run, configuration), configuration.skillVersionNumbers()));
        Map<String, String> expectedArguments = readStringMap(testCase.getExpectedArgumentsJson());
        boolean denyExpected = "DENY".equals(testCase.getExpectedOutcome());
        try {
            AgentEvaluationExecution execution = runtime.execute(
                    evaluationSessionId(run.getId(), testCase.getScenarioCode()), role(run), testCase.getInputText());
            if (denyExpected) {
                return outcome(testCase, expectedArguments, execution, false, false, false,
                        false, AgentEvaluationFailureType.SAFETY_ERROR, "安全请求未被拒绝", startedAt);
            }
            boolean routing = Objects.equals(testCase.getExpectedSkillCode(), execution.actualSkill().name());
            boolean arguments = expectedArguments.entrySet().stream()
                    .allMatch(entry -> Objects.equals(entry.getValue(), execution.actualArguments().get(entry.getKey())));
            boolean structure = execution.data() != null && execution.data().type() != null;
            boolean query = structure && forbiddenFieldsAbsent(testCase, execution);
            Boolean citation = citationResult(testCase, execution);
            AgentEvaluationFailureType failure = failure(routing, arguments, query, structure, true, citation);
            return outcome(testCase, expectedArguments, execution, routing, arguments, query,
                    structure, failure, failure == null ? null : "确定性断言未通过", startedAt);
        } catch (RuntimeException exception) {
            if (isInfrastructure(exception)) {
                throw new AgentEvaluationInfrastructureException("模型服务暂不可用", exception);
            }
            if (denyExpected) {
                return new AgentEvaluationCaseOutcome(testCase.getId(), testCase.getCategory(),
                        testCase.getExpectedSkillCode(), null, expectedArguments, Map.of(),
                        true, true, true, true, true, null, elapsed(startedAt), null, null);
            }
            AgentEvaluationFailureType type = exception instanceof LlmException
                    ? AgentEvaluationFailureType.MODEL_ERROR : AgentEvaluationFailureType.ROUTING_ERROR;
            return new AgentEvaluationCaseOutcome(testCase.getId(), testCase.getCategory(),
                    testCase.getExpectedSkillCode(), null, expectedArguments, Map.of(),
                    false, false, false, false, true, null, elapsed(startedAt), type,
                    exception.getClass().getSimpleName() + ": 评测样例执行失败");
        }
    }

    private AgentSkillRouter router(AgentEvaluationRunEntity run, RunConfiguration configuration) {
        LlmOrchestrationService candidateLlm = (templateCode, context) ->
                llm.generateJsonForEvaluation(templateCode, configuration.routerPromptVersionId(),
                        context, run.getId());
        LlmAgentSkillRouter delegate = new LlmAgentSkillRouter(candidateLlm, json, registry);
        return (question, currentSkill) -> delegate.route(question, currentSkill, configuration.definitions());
    }

    private RunConfiguration loadConfiguration(Long runId) {
        List<AgentEvaluationRunBindingEntity> values = bindings.selectList(
                new LambdaQueryWrapper<AgentEvaluationRunBindingEntity>()
                        .eq(AgentEvaluationRunBindingEntity::getEvaluationRunId, runId));
        Long promptVersion = values.stream()
                .filter(item -> "PROMPT".equals(item.getBindingType()))
                .filter(item -> "AGENT_SKILL_ROUTER".equals(item.getBindingCode()))
                .map(AgentEvaluationRunBindingEntity::getVersionId).findFirst()
                .orElseThrow(() -> new AgentEvaluationInfrastructureException("缺少路由 Prompt 版本绑定"));
        List<AgentSkillDefinition> definitions = new ArrayList<>();
        Map<AgentSkillCode, Integer> versionNumbers = new EnumMap<>(AgentSkillCode.class);
        for (AgentEvaluationRunBindingEntity binding : values) {
            if (!"SKILL".equals(binding.getBindingType())) continue;
            AgentSkillCode code;
            try { code = AgentSkillCode.valueOf(binding.getBindingCode()); }
            catch (IllegalArgumentException exception) {
                throw new AgentEvaluationInfrastructureException("Skill 版本绑定无效", exception);
            }
            AgentSkillVersionEntity version = skillVersions.selectById(binding.getVersionId());
            AgentSkillEntity skill = version == null ? null : skills.selectById(version.getSkillId());
            if (version == null || skill == null || !code.name().equals(skill.getSkillCode())) {
                throw new AgentEvaluationInfrastructureException("Skill 版本快照不存在");
            }
            definitions.add(new AgentSkillDefinition(code, skill.getName(), skill.getDescription(),
                    version.getVersion(), version.getRoutingExamplesJson(), version.getWorkflowPrompt()));
            versionNumbers.put(code, version.getVersion());
        }
        if (definitions.isEmpty()) throw new AgentEvaluationInfrastructureException("评测没有绑定 Skill 版本");
        return new RunConfiguration(promptVersion, List.copyOf(definitions), Map.copyOf(versionNumbers));
    }

    private AgentEvaluationCaseOutcome outcome(AgentEvaluationCaseEntity testCase,
                                                Map<String, String> expectedArguments,
                                                AgentEvaluationExecution execution,
                                                boolean routing, boolean arguments, boolean query,
                                                boolean structure, AgentEvaluationFailureType failure,
                                                String summary, long startedAt) {
        Boolean citation = citationResult(testCase, execution);
        return new AgentEvaluationCaseOutcome(testCase.getId(), testCase.getCategory(),
                testCase.getExpectedSkillCode(), execution.actualSkill().name(), expectedArguments,
                execution.actualArguments(), routing, arguments, query, structure,
                failure != AgentEvaluationFailureType.SAFETY_ERROR, citation, elapsed(startedAt),
                failure, summary);
    }

    private AgentEvaluationFailureType failure(boolean routing, boolean arguments, boolean query,
                                                boolean structure, boolean safety, Boolean citation) {
        if (!safety) return AgentEvaluationFailureType.SAFETY_ERROR;
        if (!routing) return AgentEvaluationFailureType.ROUTING_ERROR;
        if (!arguments) return AgentEvaluationFailureType.ARGUMENT_ERROR;
        if (!structure) return AgentEvaluationFailureType.STRUCTURE_ERROR;
        if (Boolean.FALSE.equals(citation)) return AgentEvaluationFailureType.CITATION_ERROR;
        if (!query) return AgentEvaluationFailureType.QUERY_ASSERTION_ERROR;
        return null;
    }

    private Boolean citationResult(AgentEvaluationCaseEntity testCase, AgentEvaluationExecution execution) {
        JsonNode assertions = readTree(testCase.getExpectedAssertionsJson());
        String prefix = assertions.path("requiredCitationPrefix").asText("");
        if (prefix.isBlank()) return null;
        String audience = assertions.path("audience").asText("");
        return execution.citations().stream().anyMatch(citation ->
                citation.chunkId().startsWith(prefix) && audience.equals(citation.audience()));
    }

    private boolean forbiddenFieldsAbsent(AgentEvaluationCaseEntity testCase,
                                          AgentEvaluationExecution execution) {
        JsonNode assertions = readTree(testCase.getExpectedAssertionsJson());
        String serialized;
        try { serialized = json.writeValueAsString(execution); }
        catch (Exception exception) { return false; }
        for (JsonNode field : assertions.path("forbiddenFields")) {
            if (serialized.contains("\"" + field.asText() + "\"")) return false;
        }
        return true;
    }

    private Map<String, String> readStringMap(String value) {
        try { return json.readValue(value == null ? "{}" : value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new AgentEvaluationInfrastructureException("评测参数断言无效", exception); }
    }

    private JsonNode readTree(String value) {
        try { return json.readTree(value == null ? "{}" : value); }
        catch (Exception exception) { throw new AgentEvaluationInfrastructureException("评测结构断言无效", exception); }
    }

    private UserRole role(AgentEvaluationRunEntity run) {
        if ("DOCTOR".equals(run.getTargetRole())) return UserRole.DOCTOR;
        if ("PATIENT".equals(run.getTargetRole())) return UserRole.USER;
        throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "评测角色不受支持");
    }

    private long evaluationSessionId(Long runId, String scenarioCode) {
        return Objects.hash(runId, scenarioCode) & 0x7fffffffL;
    }

    private boolean isInfrastructure(RuntimeException exception) {
        if (exception instanceof AgentEvaluationInfrastructureException) return true;
        if (!(exception instanceof LlmException)) return false;
        String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("timeout") || message.contains("timed out") || message.contains("429")
                || message.contains("unavailable") || message.contains("connection");
    }

    private long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record RunConfiguration(Long routerPromptVersionId,
                                    List<AgentSkillDefinition> definitions,
                                    Map<AgentSkillCode, Integer> skillVersionNumbers) {
    }
}
