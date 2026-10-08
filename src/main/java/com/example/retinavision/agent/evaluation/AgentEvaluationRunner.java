package com.example.retinavision.agent.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.mapper.AgentEvaluationCaseMapper;
import com.example.retinavision.mapper.AgentEvaluationResultMapper;
import com.example.retinavision.mapper.AgentEvaluationRunMapper;
import com.example.retinavision.pojo.Entity.AgentEvaluationCaseEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationResultEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AgentEvaluationRunner {
    private final AgentEvaluationRunMapper runs;
    private final AgentEvaluationCaseMapper cases;
    private final AgentEvaluationResultMapper results;
    private final AgentEvaluationCaseExecutor executor;
    private final AgentEvaluationScorer scorer;
    private final ObjectMapper json;

    public AgentEvaluationRunner(AgentEvaluationRunMapper runs, AgentEvaluationCaseMapper cases,
                                 AgentEvaluationResultMapper results, AgentEvaluationCaseExecutor executor,
                                 AgentEvaluationScorer scorer, ObjectMapper json) {
        this.runs = runs;
        this.cases = cases;
        this.results = results;
        this.executor = executor;
        this.scorer = scorer;
        this.json = json;
    }

    public void run(Long runId) {
        AgentEvaluationRunEntity run = runs.selectById(runId);
        if (run == null || !"QUEUED".equals(run.getStatus())) return;
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        runs.updateById(run);
        List<AgentEvaluationCaseOutcome> outcomes = new ArrayList<>();
        List<AgentEvaluationCaseEntity> testCases = cases.selectList(
                new LambdaQueryWrapper<AgentEvaluationCaseEntity>()
                        .eq(AgentEvaluationCaseEntity::getDatasetId, run.getDatasetId())
                        .orderByAsc(AgentEvaluationCaseEntity::getScenarioCode)
                        .orderByAsc(AgentEvaluationCaseEntity::getSequenceNo));
        try {
            if (testCases.size() != run.getTotalCount()) {
                markInvalid(run, "评测数据集样例数量不完整");
                return;
            }
            for (AgentEvaluationCaseEntity testCase : testCases) {
                AgentEvaluationRunEntity current = runs.selectById(runId);
                if (current == null) {
                    markInvalid(run, "评测运行状态不可用");
                    return;
                }
                if (Boolean.TRUE.equals(current.getCancelRequested())) {
                    finishCancelled(run, outcomes);
                    return;
                }
                AgentEvaluationCaseOutcome outcome;
                try {
                    outcome = executor.execute(run, testCase);
                } catch (AgentEvaluationInfrastructureException exception) {
                    outcome = infrastructureOutcome(testCase, exception);
                    outcomes.add(outcome);
                    persistResult(runId, outcome);
                    run.setCompletedCount(outcomes.size());
                    markInvalid(run, safeSummary(exception));
                    return;
                }
                outcomes.add(outcome);
                persistResult(runId, outcome);
                run.setCompletedCount(outcomes.size());
                runs.updateById(run);
            }
            finishScored(run, outcomes);
        } catch (RuntimeException exception) {
            markInvalid(run, safeSummary(exception));
        }
    }

    private void finishScored(AgentEvaluationRunEntity run, List<AgentEvaluationCaseOutcome> outcomes) {
        AgentEvaluationScore score = scorer.score(outcomes);
        applyScore(run, score);
        run.setStatus(score.invalid() ? "INVALID" : score.automatedPass() ? "PASSED" : "FAILED");
        run.setCompletedAt(LocalDateTime.now());
        runs.updateById(run);
    }

    private void finishCancelled(AgentEvaluationRunEntity run, List<AgentEvaluationCaseOutcome> outcomes) {
        if (!outcomes.isEmpty()) applyScore(run, scorer.score(outcomes));
        run.setAutomatedPass(false);
        run.setStatus("CANCELLED");
        run.setCompletedAt(LocalDateTime.now());
        runs.updateById(run);
    }

    private void markInvalid(AgentEvaluationRunEntity run, String summary) {
        run.setStatus("INVALID");
        run.setAutomatedPass(false);
        run.setErrorSummary(summary == null ? "评测基础设施异常" : summary.substring(0, Math.min(500, summary.length())));
        run.setCompletedAt(LocalDateTime.now());
        runs.updateById(run);
    }

    private void applyScore(AgentEvaluationRunEntity run, AgentEvaluationScore score) {
        run.setRoutingAccuracy(score.routingAccuracy());
        run.setParameterAccuracy(score.parameterAccuracy());
        run.setQueryAccuracy(score.queryAccuracy());
        run.setStructurePassRate(score.structurePassRate());
        run.setSafetyPassRate(score.safetyPassRate());
        run.setCitationPassRate(score.citationPassRate());
        run.setAverageLatencyMs(score.averageLatencyMs());
        run.setP95LatencyMs(score.p95LatencyMs());
        run.setAutomatedPass(score.automatedPass());
    }

    private void persistResult(Long runId, AgentEvaluationCaseOutcome outcome) {
        AgentEvaluationResultEntity entity = new AgentEvaluationResultEntity();
        entity.setEvaluationRunId(runId);
        entity.setEvaluationCaseId(outcome.caseId());
        entity.setActualSkillCode(outcome.actualSkill());
        entity.setActualArgumentsJson(write(outcome.actualArguments()));
        entity.setActualSummaryJson(write(Map.of(
                "routingPassed", outcome.routingPassed(),
                "parameterPassed", outcome.parameterPassed(),
                "queryPassed", outcome.queryPassed(),
                "structurePassed", outcome.structurePassed(),
                "safetyPassed", outcome.safetyPassed(),
                "citationPassed", outcome.citationPassed() == null ? "NOT_APPLICABLE" : outcome.citationPassed())));
        entity.setSuccess(outcome.failureType() == null);
        entity.setErrorType(outcome.failureType() == null ? null : outcome.failureType().name());
        entity.setErrorSummary(outcome.errorSummary());
        entity.setLatencyMs(outcome.latencyMs());
        entity.setCreatedAt(LocalDateTime.now());
        results.insert(entity);
    }

    private AgentEvaluationCaseOutcome infrastructureOutcome(AgentEvaluationCaseEntity testCase,
                                                               RuntimeException exception) {
        return new AgentEvaluationCaseOutcome(testCase.getId(), testCase.getCategory(),
                testCase.getExpectedSkillCode(), null, Map.of(), Map.of(), false, false,
                false, false, false, null, 0,
                AgentEvaluationFailureType.INFRASTRUCTURE_ERROR, safeSummary(exception));
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("无法保存评测摘要", exception); }
    }

    private String safeSummary(Throwable exception) {
        return exception.getClass().getSimpleName() + ": 评测基础设施异常";
    }
}
