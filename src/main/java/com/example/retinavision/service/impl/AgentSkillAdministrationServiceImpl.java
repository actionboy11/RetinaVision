package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.agent.AgentSkillCode;
import com.example.retinavision.agent.AgentSkillRouter;
import com.example.retinavision.agent.AgentSkillCatalogService;
import com.example.retinavision.agent.AgentSkillDefinition;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AgentSkillEvaluationRunMapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.AgentSkillVersionEntity;
import com.example.retinavision.service.AgentSkillAdministrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentSkillAdministrationServiceImpl implements AgentSkillAdministrationService {
    private static final double ROUTE_THRESHOLD = 0.90;
    private static final double PARAMETER_THRESHOLD = 0.95;
    private final AgentSkillMapper skills;
    private final AgentSkillVersionMapper versions;
    private final AgentSkillEvaluationRunMapper evaluations;
    private final AgentSkillRouter router;
    private final AgentSkillCatalogService catalog;
    private final ObjectMapper json;

    public AgentSkillAdministrationServiceImpl(AgentSkillMapper skills, AgentSkillVersionMapper versions,
                                               AgentSkillEvaluationRunMapper evaluations,
                                               AgentSkillRouter router, AgentSkillCatalogService catalog,
                                               ObjectMapper json) {
        this.skills = skills; this.versions = versions; this.evaluations = evaluations;
        this.router = router; this.catalog = catalog; this.json = json;
    }

    @Override
    public List<AgentSkillEntity> listSkills() {
        return skills.selectList(new LambdaQueryWrapper<AgentSkillEntity>().orderByAsc(AgentSkillEntity::getId));
    }

    @Override
    public List<AgentSkillVersionEntity> listVersions(String skillCode) {
        AgentSkillEntity skill = requireSkill(skillCode);
        return versions.selectList(new LambdaQueryWrapper<AgentSkillVersionEntity>()
                .eq(AgentSkillVersionEntity::getSkillId, skill.getId())
                .orderByDesc(AgentSkillVersionEntity::getVersion));
    }

    @Override
    public AgentSkillEvaluationRunEntity evaluate(String skillCode, Long versionId, Integer operatorId) {
        AgentSkillEntity skill = requireSkill(skillCode);
        AgentSkillVersionEntity version = requireVersion(skill, versionId);
        List<EvaluationExample> examples = readExamples(version.getRoutingExamplesJson());
        List<String> negativeExamples = readNegativeExamples(version.getRoutingNegativeExamplesJson());
        List<String> failures = new ArrayList<>();
        int routePassed = 0;
        int parameterPassed = 0;
        AgentSkillCode expected = AgentSkillCode.valueOf(skillCode);
        List<AgentSkillDefinition> definitions = catalog.forEvaluation(skill, version);
        for (EvaluationExample example : examples) {
            try {
                var route = router.route(example.query(), null, definitions);
                if (route.confidence() < 0.65) {
                    failures.add("低置信度: " + example.query());
                } else if (route.skillCode() == expected) {
                    routePassed++;
                    if (containsExpectedArguments(route.arguments(), example.arguments())) parameterPassed++;
                    else failures.add("参数不匹配: " + example.query());
                } else {
                    failures.add("路由不匹配: " + example.query());
                }
            } catch (RuntimeException exception) {
                failures.add("路由异常: " + example.query() + " (" + exception.getClass().getSimpleName() + ")");
            }
        }
        for (String example : negativeExamples) {
            try {
                var route = router.route(example, null, definitions);
                if (route.confidence() < 0.65 || route.skillCode() != expected) routePassed++;
                else failures.add("反例误路由: " + example);
            } catch (RuntimeException exception) {
                failures.add("路由异常: " + example + " (" + exception.getClass().getSimpleName() + ")");
            }
        }
        int total = examples.size() + negativeExamples.size();
        AgentSkillEvaluationRunEntity run = new AgentSkillEvaluationRunEntity();
        run.setSkillVersionId(versionId); run.setStatus("COMPLETED"); run.setTotalCount(total);
        run.setRoutePassedCount(routePassed); run.setParameterPassedCount(parameterPassed);
        run.setRoutingAccuracy(total == 0 ? 0 : (double) routePassed / total);
        run.setParameterAccuracy(examples.isEmpty() ? 0 : (double) parameterPassed / examples.size());
        run.setSafetyPassed(isSafeCandidate(expected, version));
        run.setFailureSamplesJson(write(failures)); run.setCreatedBy(operatorId);
        run.setCreatedAt(LocalDateTime.now()); run.setCompletedAt(LocalDateTime.now());
        evaluations.insert(run);
        return run;
    }

    @Override
    public void activate(String skillCode, Long versionId) {
        AgentSkillEntity skill = requireSkill(skillCode);
        requireVersion(skill, versionId);
        AgentSkillEvaluationRunEntity latest = evaluations.selectOne(
                new LambdaQueryWrapper<AgentSkillEvaluationRunEntity>()
                        .eq(AgentSkillEvaluationRunEntity::getSkillVersionId, versionId)
                        .orderByDesc(AgentSkillEvaluationRunEntity::getCreatedAt).last("LIMIT 1"));
        if (latest == null || !"COMPLETED".equals(latest.getStatus())
                || latest.getRoutingAccuracy() < ROUTE_THRESHOLD
                || latest.getParameterAccuracy() < PARAMETER_THRESHOLD
                || !Boolean.TRUE.equals(latest.getSafetyPassed())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 版本尚未通过启用评测");
        }
        skill.setActiveVersionId(versionId); skill.setUpdatedAt(LocalDateTime.now()); skills.updateById(skill);
    }

    private AgentSkillEntity requireSkill(String code) {
        AgentSkillEntity value = skills.selectOne(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getSkillCode, code));
        if (value == null) throw new BaseException(ErrorMessageSignal.NOT_FOUND, "Skill 不存在");
        return value;
    }

    private AgentSkillVersionEntity requireVersion(AgentSkillEntity skill, Long versionId) {
        AgentSkillVersionEntity value = versions.selectById(versionId);
        if (value == null || !skill.getId().equals(value.getSkillId()))
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "Skill 版本不存在");
        return value;
    }

    private List<EvaluationExample> readExamples(String value) {
        try {
            JsonNode root = json.readTree(value);
            if (!root.isArray()) throw new IllegalArgumentException();
            List<EvaluationExample> result = new ArrayList<>();
            for (JsonNode item : root) {
                if (item.isTextual()) {
                    result.add(new EvaluationExample(item.asText(), Map.of()));
                    continue;
                }
                String query = item.path("query").asText("").trim();
                if (query.isEmpty()) throw new IllegalArgumentException();
                Map<String, String> arguments = new LinkedHashMap<>();
                JsonNode argumentNode = item.path("arguments");
                if (argumentNode.isObject()) {
                    argumentNode.fields().forEachRemaining(entry -> arguments.put(entry.getKey(), entry.getValue().asText()));
                }
                result.add(new EvaluationExample(query, arguments));
            }
            return result;
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 路由示例格式无效");
        }
    }

    private List<String> readNegativeExamples(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            JsonNode root = json.readTree(value);
            if (!root.isArray()) throw new IllegalArgumentException();
            List<String> result = new ArrayList<>();
            for (JsonNode item : root) {
                String query = item.isTextual() ? item.asText() : item.path("query").asText("");
                if (!query.isBlank()) result.add(query);
            }
            return result;
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 路由反例格式无效");
        }
    }

    private boolean containsExpectedArguments(Map<String, String> actual, Map<String, String> expected) {
        if (actual == null) return expected.isEmpty();
        return expected.entrySet().stream().allMatch(entry -> entry.getValue().equals(actual.get(entry.getKey())));
    }

    private boolean isReadOnlySkill(AgentSkillCode skillCode) {
        return switch (skillCode) {
            case DOCTOR_WORKLOAD_OVERVIEW, ASSIGNED_CASE_SEARCH, CASE_CLINICAL_SUMMARY,
                    CASE_FOLLOWUP_ANALYSIS, DOCTOR_TASK_SEARCH, DOCTOR_CLINICAL_QUEUE,
                    MEDICAL_KNOWLEDGE_QA, MY_CASE_LIST, MY_CASE_PROGRESS,
                    MY_SIGNED_REPORT, PATIENT_KNOWLEDGE_QA -> true;
        };
    }

    private boolean isSafeCandidate(AgentSkillCode skillCode, AgentSkillVersionEntity version) {
        if (!isReadOnlySkill(skillCode)) return false;
        String workflow = version.getWorkflowPrompt() == null ? "" : version.getWorkflowPrompt();
        return java.util.stream.Stream.of("执行删除", "创建任务", "修改病例", "保存审核", "签发 PDF", "签发PDF")
                .noneMatch(workflow::contains);
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { return "[]"; }
    }

    private record EvaluationExample(String query, Map<String, String> arguments) {
    }
}
