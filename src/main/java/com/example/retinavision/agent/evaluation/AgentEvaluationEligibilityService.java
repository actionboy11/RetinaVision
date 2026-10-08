package com.example.retinavision.agent.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.agent.AgentSkillCode;
import com.example.retinavision.agent.AgentSkillRegistry;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.mapper.AgentEvaluationDatasetMapper;
import com.example.retinavision.mapper.AgentEvaluationRunBindingMapper;
import com.example.retinavision.mapper.AgentEvaluationRunMapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.PromptTemplateMapper;
import com.example.retinavision.pojo.Entity.AgentEvaluationDatasetEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunBindingEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentEvaluationEligibilityService {
    private static final String ROUTER_PROMPT = "AGENT_SKILL_ROUTER";
    private final AgentEvaluationRunMapper runs;
    private final AgentEvaluationRunBindingMapper bindings;
    private final AgentEvaluationDatasetMapper datasets;
    private final AgentSkillMapper skills;
    private final PromptTemplateMapper prompts;
    private final AgentSkillRegistry registry;
    private final LlmProperties llm;

    public AgentEvaluationEligibilityService(AgentEvaluationRunMapper runs,
                                             AgentEvaluationRunBindingMapper bindings,
                                             AgentEvaluationDatasetMapper datasets,
                                             AgentSkillMapper skills,
                                             PromptTemplateMapper prompts,
                                             AgentSkillRegistry registry,
                                             LlmProperties llm) {
        this.runs = runs;
        this.bindings = bindings;
        this.datasets = datasets;
        this.skills = skills;
        this.prompts = prompts;
        this.registry = registry;
        this.llm = llm;
    }

    public void requireSkillEligible(String skillCode, Long versionId) {
        AgentSkillCode candidate = parseSkill(skillCode);
        UserRole role = registry.isAvailable(candidate, UserRole.USER) ? UserRole.USER : UserRole.DOCTOR;
        String targetRole = role == UserRole.USER ? "PATIENT" : "DOCTOR";
        boolean eligible = candidateBindings("SKILL", skillCode, versionId).stream()
                .anyMatch(binding -> eligibleRun(binding.getEvaluationRunId(), targetRole)
                        && snapshotMatches(binding.getEvaluationRunId(), role,
                        skillCode, versionId, null));
        if (!eligible) throw notEligible();
    }

    public void requirePromptEligible(String templateCode, Long versionId) {
        if (!ROUTER_PROMPT.equals(templateCode)) throw notEligible();
        List<AgentEvaluationRunBindingEntity> candidates = candidateBindings("PROMPT", templateCode, versionId);
        boolean eligible = List.of(UserRole.DOCTOR, UserRole.USER).stream().allMatch(role -> {
            String targetRole = role == UserRole.USER ? "PATIENT" : "DOCTOR";
            return candidates.stream().anyMatch(binding -> eligibleRun(binding.getEvaluationRunId(), targetRole)
                    && snapshotMatches(binding.getEvaluationRunId(), role,
                    null, null, versionId));
        });
        if (!eligible) throw notEligible();
    }

    private List<AgentEvaluationRunBindingEntity> candidateBindings(String type, String code, Long versionId) {
        return bindings.selectList(new LambdaQueryWrapper<AgentEvaluationRunBindingEntity>()
                .eq(AgentEvaluationRunBindingEntity::getBindingType, type)
                .eq(AgentEvaluationRunBindingEntity::getBindingCode, code)
                .eq(AgentEvaluationRunBindingEntity::getVersionId, versionId)
                .orderByDesc(AgentEvaluationRunBindingEntity::getId));
    }

    private boolean eligibleRun(Long runId, String role) {
        AgentEvaluationRunEntity run = runs.selectById(runId);
        if (run == null
                || !role.equals(run.getTargetRole())
                || !"PASSED".equals(run.getStatus())
                || !Boolean.TRUE.equals(run.getAutomatedPass())
                || !"APPROVED".equals(run.getReviewDecision())
                || !llm.getProvider().equals(run.getProvider())
                || !llm.getModel().equals(run.getModel())) {
            return false;
        }
        AgentEvaluationDatasetEntity currentDataset = datasets.selectOne(
                new LambdaQueryWrapper<AgentEvaluationDatasetEntity>()
                        .eq(AgentEvaluationDatasetEntity::getTargetRole, role)
                        .eq(AgentEvaluationDatasetEntity::getStatus, "ACTIVE")
                        .orderByDesc(AgentEvaluationDatasetEntity::getVersion)
                        .last("LIMIT 1"));
        return currentDataset != null
                && currentDataset.getId().equals(run.getDatasetId())
                && currentDataset.getVersion().equals(run.getDatasetVersion());
    }

    private boolean snapshotMatches(Long runId, UserRole role,
                                    String candidateSkillCode, Long candidateSkillVersion,
                                    Long candidatePromptVersion) {
        Map<String, Long> expectedSkills = currentSkillVersions(role);
        if (candidateSkillCode != null) {
            AgentSkillCode candidate = parseSkill(candidateSkillCode);
            if (!registry.isAvailable(candidate, role)) return false;
            expectedSkills.put(candidateSkillCode, candidateSkillVersion);
        }

        PromptTemplateEntity routerPrompt = prompts.selectOne(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTemplateCode, ROUTER_PROMPT));
        Long expectedPromptVersion = candidatePromptVersion != null
                ? candidatePromptVersion
                : routerPrompt == null ? null : routerPrompt.getActiveVersionId();
        if (expectedPromptVersion == null || expectedSkills.isEmpty()) return false;

        Map<String, Long> actualSkills = new LinkedHashMap<>();
        Long actualPromptVersion = null;
        for (AgentEvaluationRunBindingEntity binding : bindings.selectList(
                new LambdaQueryWrapper<AgentEvaluationRunBindingEntity>()
                        .eq(AgentEvaluationRunBindingEntity::getEvaluationRunId, runId))) {
            if ("SKILL".equals(binding.getBindingType())) {
                AgentSkillCode code;
                try {
                    code = AgentSkillCode.valueOf(binding.getBindingCode());
                } catch (IllegalArgumentException exception) {
                    return false;
                }
                if (!registry.isAvailable(code, role)
                        || actualSkills.put(binding.getBindingCode(), binding.getVersionId()) != null) {
                    return false;
                }
            } else if ("PROMPT".equals(binding.getBindingType())
                    && ROUTER_PROMPT.equals(binding.getBindingCode())
                    && actualPromptVersion == null) {
                actualPromptVersion = binding.getVersionId();
            } else {
                return false;
            }
        }
        return expectedSkills.equals(actualSkills) && expectedPromptVersion.equals(actualPromptVersion);
    }

    private Map<String, Long> currentSkillVersions(UserRole role) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (AgentSkillEntity skill : skills.selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getStatus, "ACTIVE"))) {
            AgentSkillCode code;
            try {
                code = AgentSkillCode.valueOf(skill.getSkillCode());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (registry.isAvailable(code, role) && skill.getActiveVersionId() != null) {
                result.put(skill.getSkillCode(), skill.getActiveVersionId());
            }
        }
        return result;
    }

    private AgentSkillCode parseSkill(String skillCode) {
        try {
            return AgentSkillCode.valueOf(skillCode);
        } catch (IllegalArgumentException exception) {
            throw notEligible();
        }
    }

    private BaseException notEligible() {
        return new BaseException(ErrorMessageSignal.PARAM_ERROR,
                "候选版本须先通过匹配当前模型、数据集与完整版本快照的 Agent 评测并经管理员批准");
    }
}
