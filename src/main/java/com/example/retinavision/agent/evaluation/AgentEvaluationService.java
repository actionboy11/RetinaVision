package com.example.retinavision.agent.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.mapper.AgentEvaluationDatasetMapper;
import com.example.retinavision.mapper.AgentEvaluationRunBindingMapper;
import com.example.retinavision.mapper.AgentEvaluationRunMapper;
import com.example.retinavision.pojo.Entity.AgentEvaluationDatasetEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunBindingEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

@Service
public class AgentEvaluationService {
    public static final String PRIMARY_MODEL_KEY = "ALIYUN_PRIMARY";
    private static final Set<String> ACTIVE_STATUSES = Set.of("QUEUED", "RUNNING");
    private final AgentEvaluationDatasetMapper datasets;
    private final AgentEvaluationRunMapper runs;
    private final AgentEvaluationRunBindingMapper bindings;
    private final AgentEvaluationRunner runner;
    private final LlmProperties llm;
    private final Executor executor;

    public AgentEvaluationService(AgentEvaluationDatasetMapper datasets, AgentEvaluationRunMapper runs,
                                  AgentEvaluationRunBindingMapper bindings, AgentEvaluationRunner runner,
                                  LlmProperties llm,
                                  @Qualifier("agentEvaluationExecutor") Executor executor) {
        this.datasets = datasets;
        this.runs = runs;
        this.bindings = bindings;
        this.runner = runner;
        this.llm = llm;
        this.executor = executor;
    }

    public synchronized AgentEvaluationRunEntity start(AgentEvaluationStartCommand command) {
        if (!PRIMARY_MODEL_KEY.equals(command.modelKey())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "模型配置键不受支持");
        }
        AgentEvaluationDatasetEntity dataset = datasets.selectById(command.datasetId());
        if (dataset == null || !"ACTIVE".equals(dataset.getStatus())
                || !dataset.getTargetRole().equals(command.targetRole())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "评测集与目标角色不匹配");
        }
        long active = runs.selectCount(new LambdaQueryWrapper<AgentEvaluationRunEntity>()
                .in(AgentEvaluationRunEntity::getStatus, ACTIVE_STATUSES));
        if (active > 0) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "当前已有真实模型评测正在运行");
        }
        if (command.skillVersions() == null || command.skillVersions().isEmpty()
                || command.promptVersions() == null
                || !command.promptVersions().containsKey("AGENT_SKILL_ROUTER")) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "必须冻结 Skill 与路由 Prompt 版本");
        }
        AgentEvaluationRunEntity run = new AgentEvaluationRunEntity();
        run.setDatasetId(dataset.getId());
        run.setDatasetVersion(dataset.getVersion());
        run.setTargetRole(command.targetRole());
        run.setModelKey(command.modelKey());
        run.setProvider(llm.getProvider());
        run.setModel(llm.getModel());
        run.setStatus("QUEUED");
        run.setTotalCount(dataset.getExpectedCaseCount());
        run.setCompletedCount(0);
        run.setCancelRequested(false);
        run.setReviewDecision("PENDING");
        run.setCreatedBy(command.createdBy());
        run.setCreatedAt(LocalDateTime.now());
        runs.insert(run);
        command.skillVersions().forEach((code, id) -> insertBinding(run.getId(), "SKILL", code, id));
        command.promptVersions().forEach((code, id) -> insertBinding(run.getId(), "PROMPT", code, id));
        executor.execute(() -> runner.run(run.getId()));
        return run;
    }

    public void requestCancel(Long runId) {
        AgentEvaluationRunEntity run = runs.selectById(runId);
        if (run == null) throw new BaseException(ErrorMessageSignal.NOT_FOUND, "评测运行不存在");
        if (ACTIVE_STATUSES.contains(run.getStatus())) {
            run.setCancelRequested(true);
            runs.updateById(run);
        }
    }

    private void insertBinding(Long runId, String type, String code, Long versionId) {
        AgentEvaluationRunBindingEntity binding = new AgentEvaluationRunBindingEntity();
        binding.setEvaluationRunId(runId);
        binding.setBindingType(type);
        binding.setBindingCode(code);
        binding.setVersionId(versionId);
        binding.setVersionLabel(String.valueOf(versionId));
        binding.setCreatedAt(LocalDateTime.now());
        bindings.insert(binding);
    }
}
