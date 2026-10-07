package com.example.retinavision.agent.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.mapper.AgentEvaluationRunBindingMapper;
import com.example.retinavision.mapper.AgentEvaluationRunMapper;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunBindingEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class AgentEvaluationEligibilityService {
    private static final Set<String> PATIENT_SKILLS = Set.of(
            "MY_CASE_LIST", "MY_CASE_PROGRESS", "MY_SIGNED_REPORT", "PATIENT_KNOWLEDGE_QA");
    private final AgentEvaluationRunMapper runs;
    private final AgentEvaluationRunBindingMapper bindings;
    private final LlmProperties llm;

    public AgentEvaluationEligibilityService(AgentEvaluationRunMapper runs,
                                             AgentEvaluationRunBindingMapper bindings,
                                             LlmProperties llm) {
        this.runs = runs;
        this.bindings = bindings;
        this.llm = llm;
    }

    public void requireSkillEligible(String skillCode, Long versionId) {
        String role = PATIENT_SKILLS.contains(skillCode) ? "PATIENT" : "DOCTOR";
        boolean eligible = candidateBindings("SKILL", skillCode, versionId).stream()
                .anyMatch(binding -> eligibleRun(binding.getEvaluationRunId(), role)
                        && hasCounterpart(binding.getEvaluationRunId(), "PROMPT", "AGENT_SKILL_ROUTER"));
        if (!eligible) throw notEligible();
    }

    public void requirePromptEligible(String templateCode, Long versionId) {
        List<AgentEvaluationRunBindingEntity> candidates = candidateBindings("PROMPT", templateCode, versionId);
        boolean eligible = Set.of("DOCTOR", "PATIENT").stream().allMatch(role -> candidates.stream()
                .anyMatch(binding -> eligibleRun(binding.getEvaluationRunId(), role)
                        && hasAnyCounterpart(binding.getEvaluationRunId(), "SKILL")));
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
        return run != null
                && (role == null || role.equals(run.getTargetRole()))
                && "PASSED".equals(run.getStatus())
                && Boolean.TRUE.equals(run.getAutomatedPass())
                && "APPROVED".equals(run.getReviewDecision())
                && llm.getProvider().equals(run.getProvider())
                && llm.getModel().equals(run.getModel());
    }

    private boolean hasCounterpart(Long runId, String type, String code) {
        return bindings.selectCount(new LambdaQueryWrapper<AgentEvaluationRunBindingEntity>()
                .eq(AgentEvaluationRunBindingEntity::getEvaluationRunId, runId)
                .eq(AgentEvaluationRunBindingEntity::getBindingType, type)
                .eq(AgentEvaluationRunBindingEntity::getBindingCode, code)) > 0;
    }

    private boolean hasAnyCounterpart(Long runId, String type) {
        return bindings.selectCount(new LambdaQueryWrapper<AgentEvaluationRunBindingEntity>()
                .eq(AgentEvaluationRunBindingEntity::getEvaluationRunId, runId)
                .eq(AgentEvaluationRunBindingEntity::getBindingType, type)) > 0;
    }

    private BaseException notEligible() {
        return new BaseException(ErrorMessageSignal.PARAM_ERROR,
                "候选版本须先通过匹配当前模型与角色数据集的 Agent 评测并经管理员批准");
    }
}
