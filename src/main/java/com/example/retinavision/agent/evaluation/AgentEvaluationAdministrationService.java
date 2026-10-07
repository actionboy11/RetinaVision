package com.example.retinavision.agent.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.mapper.*;
import com.example.retinavision.pojo.Entity.*;
import com.example.retinavision.pojo.VO.*;
import com.example.retinavision.result.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgentEvaluationAdministrationService {
    private final AgentEvaluationDatasetMapper datasets;
    private final AgentEvaluationRunMapper runs;
    private final AgentEvaluationRunBindingMapper bindings;
    private final AgentEvaluationResultMapper results;
    private final AgentEvaluationCaseMapper cases;
    private final AgentSkillMapper skills;
    private final AgentSkillVersionMapper skillVersions;
    private final PromptTemplateMapper prompts;
    private final PromptTemplateVersionMapper promptVersions;
    private final LlmProperties llm;
    private final ObjectMapper json;

    public AgentEvaluationAdministrationService(AgentEvaluationDatasetMapper datasets,
                                                AgentEvaluationRunMapper runs,
                                                AgentEvaluationRunBindingMapper bindings,
                                                AgentEvaluationResultMapper results,
                                                AgentEvaluationCaseMapper cases,
                                                AgentSkillMapper skills,
                                                AgentSkillVersionMapper skillVersions,
                                                PromptTemplateMapper prompts,
                                                PromptTemplateVersionMapper promptVersions,
                                                LlmProperties llm, ObjectMapper json) {
        this.datasets = datasets; this.runs = runs; this.bindings = bindings; this.results = results;
        this.cases = cases; this.skills = skills; this.skillVersions = skillVersions;
        this.prompts = prompts; this.promptVersions = promptVersions; this.llm = llm; this.json = json;
    }

    public AgentEvaluationOptionsVO options() {
        Map<Long, AgentSkillEntity> skillById = skills.selectList(null).stream()
                .collect(Collectors.toMap(AgentSkillEntity::getId, item -> item));
        Map<String, List<AgentEvaluationOptionsVO.VersionOption>> skillOptions = new TreeMap<>();
        for (AgentSkillVersionEntity version : skillVersions.selectList(
                new LambdaQueryWrapper<AgentSkillVersionEntity>().orderByDesc(AgentSkillVersionEntity::getVersion))) {
            AgentSkillEntity skill = skillById.get(version.getSkillId());
            if (skill == null) continue;
            skillOptions.computeIfAbsent(skill.getSkillCode(), ignored -> new ArrayList<>()).add(
                    new AgentEvaluationOptionsVO.VersionOption(version.getId(), version.getVersion(),
                            version.getId().equals(skill.getActiveVersionId())));
        }
        Map<Long, PromptTemplateEntity> promptById = prompts.selectList(null).stream()
                .collect(Collectors.toMap(PromptTemplateEntity::getId, item -> item));
        Map<String, List<AgentEvaluationOptionsVO.VersionOption>> promptOptions = new TreeMap<>();
        for (PromptTemplateVersionEntity version : promptVersions.selectList(
                new LambdaQueryWrapper<PromptTemplateVersionEntity>()
                        .orderByDesc(PromptTemplateVersionEntity::getVersion))) {
            PromptTemplateEntity prompt = promptById.get(version.getTemplateId());
            if (prompt == null || !"AGENT_SKILL_ROUTER".equals(prompt.getTemplateCode())) continue;
            promptOptions.computeIfAbsent(prompt.getTemplateCode(), ignored -> new ArrayList<>()).add(
                    new AgentEvaluationOptionsVO.VersionOption(version.getId(), version.getVersion(),
                            version.getId().equals(prompt.getActiveVersionId())));
        }
        return new AgentEvaluationOptionsVO(List.of(new AgentEvaluationOptionsVO.ModelOption(
                AgentEvaluationService.PRIMARY_MODEL_KEY, llm.getProvider(), llm.getModel())),
                skillOptions, promptOptions);
    }

    public List<AgentEvaluationDatasetVO> listDatasets() {
        return datasets.selectList(new LambdaQueryWrapper<AgentEvaluationDatasetEntity>()
                        .orderByAsc(AgentEvaluationDatasetEntity::getTargetRole)
                        .orderByDesc(AgentEvaluationDatasetEntity::getVersion)).stream()
                .map(item -> new AgentEvaluationDatasetVO(item.getId(), item.getDatasetCode(), item.getName(),
                        item.getTargetRole(), item.getVersion(), item.getStatus(), item.getExpectedCaseCount(),
                        item.getDescription())).toList();
    }

    public PageResult<AgentEvaluationRunVO> listRuns(String role, String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, pageSize));
        Page<AgentEvaluationRunEntity> value = runs.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<AgentEvaluationRunEntity>()
                        .eq(role != null && !role.isBlank(), AgentEvaluationRunEntity::getTargetRole, role)
                        .eq(status != null && !status.isBlank(), AgentEvaluationRunEntity::getStatus, status)
                        .orderByDesc(AgentEvaluationRunEntity::getCreatedAt));
        return new PageResult<>(value.getRecords().stream().map(item -> toRun(item, List.of())).toList(),
                value.getTotal(), safePage, safeSize);
    }

    public AgentEvaluationRunVO getRun(Long runId) {
        AgentEvaluationRunEntity run = requireRun(runId);
        List<AgentEvaluationRunBindingEntity> snapshot = bindings.selectList(
                new LambdaQueryWrapper<AgentEvaluationRunBindingEntity>()
                        .eq(AgentEvaluationRunBindingEntity::getEvaluationRunId, runId)
                        .orderByAsc(AgentEvaluationRunBindingEntity::getBindingType)
                        .orderByAsc(AgentEvaluationRunBindingEntity::getBindingCode));
        return toRun(run, snapshot);
    }

    public PageResult<AgentEvaluationResultVO> listResults(Long runId, Boolean success, String errorType,
                                                           int page, int pageSize) {
        requireRun(runId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        Page<AgentEvaluationResultEntity> values = results.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<AgentEvaluationResultEntity>()
                        .eq(AgentEvaluationResultEntity::getEvaluationRunId, runId)
                        .eq(success != null, AgentEvaluationResultEntity::getSuccess, success)
                        .eq(errorType != null && !errorType.isBlank(), AgentEvaluationResultEntity::getErrorType, errorType)
                        .orderByAsc(AgentEvaluationResultEntity::getId));
        Map<Long, AgentEvaluationCaseEntity> casesById = cases.selectBatchIds(values.getRecords().stream()
                        .map(AgentEvaluationResultEntity::getEvaluationCaseId).toList()).stream()
                .collect(Collectors.toMap(AgentEvaluationCaseEntity::getId, item -> item));
        List<AgentEvaluationResultVO> records = values.getRecords().stream().map(result -> {
            AgentEvaluationCaseEntity testCase = casesById.get(result.getEvaluationCaseId());
            return new AgentEvaluationResultVO(result.getEvaluationCaseId(),
                    testCase == null ? null : testCase.getCategory(),
                    testCase == null ? null : abbreviate(testCase.getInputText(), 200),
                    testCase == null ? null : testCase.getExpectedSkillCode(), result.getActualSkillCode(),
                    testCase == null ? Map.of() : readMap(testCase.getExpectedArgumentsJson()),
                    readMap(result.getActualArgumentsJson()), result.getSuccess(), result.getErrorType(),
                    result.getErrorSummary(), result.getLatencyMs());
        }).toList();
        return new PageResult<>(records, values.getTotal(), safePage, safeSize);
    }

    public AgentEvaluationRunVO review(Long runId, String decision, String note, Integer reviewerId) {
        AgentEvaluationRunEntity run = requireRun(runId);
        if (!Set.of("APPROVED", "REJECTED").contains(decision)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "评审结论无效");
        }
        if ("APPROVED".equals(decision)
                && (!"PASSED".equals(run.getStatus()) || !Boolean.TRUE.equals(run.getAutomatedPass()))) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "仅自动评测通过的运行可以批准");
        }
        if (!Set.of("PASSED", "FAILED").contains(run.getStatus())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "当前运行状态不可评审");
        }
        run.setReviewDecision(decision);
        run.setReviewNote(abbreviate(note, 1000));
        run.setReviewedBy(reviewerId);
        run.setReviewedAt(LocalDateTime.now());
        runs.updateById(run);
        return getRun(runId);
    }

    private AgentEvaluationRunEntity requireRun(Long runId) {
        AgentEvaluationRunEntity run = runs.selectById(runId);
        if (run == null) throw new BaseException(ErrorMessageSignal.NOT_FOUND, "评测运行不存在");
        return run;
    }

    private AgentEvaluationRunVO toRun(AgentEvaluationRunEntity run,
                                       List<AgentEvaluationRunBindingEntity> snapshot) {
        AgentEvaluationRunVO.Metrics metrics = run.getRoutingAccuracy() == null ? null
                : new AgentEvaluationRunVO.Metrics(run.getRoutingAccuracy(), run.getParameterAccuracy(),
                run.getQueryAccuracy(), run.getStructurePassRate(), run.getSafetyPassRate(),
                run.getCitationPassRate(), run.getAverageLatencyMs(), run.getP95LatencyMs());
        return new AgentEvaluationRunVO(run.getId(), run.getDatasetId(), run.getDatasetVersion(),
                run.getTargetRole(), run.getModelKey(), run.getProvider(), run.getModel(), run.getStatus(),
                new AgentEvaluationRunVO.Progress(run.getCompletedCount(), run.getTotalCount()), metrics,
                run.getAutomatedPass(), run.getReviewDecision(), run.getReviewNote(), snapshot.stream()
                .map(item -> new AgentEvaluationRunVO.Binding(item.getBindingType(), item.getBindingCode(),
                        item.getVersionId(), item.getVersionLabel())).toList(),
                run.getCreatedAt(), run.getCompletedAt());
    }

    private Map<String, String> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception ignored) { return Map.of(); }
    }

    private String abbreviate(String value, int limit) {
        if (value == null) return null;
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
