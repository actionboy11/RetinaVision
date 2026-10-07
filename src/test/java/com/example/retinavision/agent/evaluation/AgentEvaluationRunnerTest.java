package com.example.retinavision.agent.evaluation;

import com.example.retinavision.mapper.AgentEvaluationCaseMapper;
import com.example.retinavision.mapper.AgentEvaluationResultMapper;
import com.example.retinavision.mapper.AgentEvaluationRunMapper;
import com.example.retinavision.pojo.Entity.AgentEvaluationCaseEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationResultEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentEvaluationRunnerTest {

    @Test
    void executesSequentiallyPersistsResultsAndCompletesMetrics() {
        Fixture fixture = fixture(2);
        AtomicInteger sequence = new AtomicInteger();
        AgentEvaluationCaseExecutor executor = (run, testCase) -> {
            assertThat(testCase.getSequenceNo()).isEqualTo(sequence.incrementAndGet());
            return success(testCase.getId(), 100L * testCase.getSequenceNo());
        };
        AgentEvaluationRunner runner = fixture.runner(executor);

        runner.run(91L);

        ArgumentCaptor<AgentEvaluationResultEntity> results =
                ArgumentCaptor.forClass(AgentEvaluationResultEntity.class);
        verify(fixture.results, times(2)).insert(results.capture());
        assertThat(results.getAllValues()).allSatisfy(result -> {
            assertThat(result.getEvaluationRunId()).isEqualTo(91L);
            assertThat(result.getSuccess()).isTrue();
        });
        assertThat(fixture.run.getStatus()).isEqualTo("PASSED");
        assertThat(fixture.run.getCompletedCount()).isEqualTo(2);
        assertThat(fixture.run.getAutomatedPass()).isTrue();
    }

    @Test
    void stopsBetweenCasesWhenCancellationIsRequested() {
        Fixture fixture = fixture(3);
        AtomicInteger calls = new AtomicInteger();
        AgentEvaluationRunner runner = fixture.runner((run, testCase) -> {
            calls.incrementAndGet();
            run.setCancelRequested(true);
            return success(testCase.getId(), 50);
        });

        runner.run(91L);

        assertThat(calls).hasValue(1);
        assertThat(fixture.run.getStatus()).isEqualTo("CANCELLED");
        assertThat(fixture.run.getCompletedCount()).isEqualTo(1);
    }

    @Test
    void infrastructureFailureMarksRunInvalidWithoutFabricatingBusinessFailure() {
        Fixture fixture = fixture(2);
        AgentEvaluationRunner runner = fixture.runner((run, testCase) -> {
            throw new AgentEvaluationInfrastructureException("模型服务暂不可用");
        });

        runner.run(91L);

        assertThat(fixture.run.getStatus()).isEqualTo("INVALID");
        assertThat(fixture.run.getAutomatedPass()).isFalse();
        assertThat(fixture.run.getErrorSummary()).contains("模型服务");
        verify(fixture.results).insert(any(AgentEvaluationResultEntity.class));
    }

    private AgentEvaluationCaseOutcome success(long caseId, long latency) {
        return new AgentEvaluationCaseOutcome(caseId, "DOCTOR_QUERY", "ASSIGNED_CASE_SEARCH",
                "ASSIGNED_CASE_SEARCH", Map.of(), Map.of(), true, true, true,
                true, true, null, latency, null, null);
    }

    private Fixture fixture(int count) {
        AgentEvaluationRunMapper runs = mock(AgentEvaluationRunMapper.class);
        AgentEvaluationCaseMapper cases = mock(AgentEvaluationCaseMapper.class);
        AgentEvaluationResultMapper results = mock(AgentEvaluationResultMapper.class);
        AgentEvaluationRunEntity run = new AgentEvaluationRunEntity();
        run.setId(91L); run.setDatasetId(7L); run.setStatus("QUEUED"); run.setCancelRequested(false);
        run.setTotalCount(count); run.setCompletedCount(0);
        when(runs.selectById(91L)).thenReturn(run);
        List<AgentEvaluationCaseEntity> caseList = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> {
                    AgentEvaluationCaseEntity item = new AgentEvaluationCaseEntity();
                    item.setId((long) index); item.setDatasetId(7L); item.setSequenceNo(index);
                    item.setScenarioCode("S-" + index); item.setCategory("DOCTOR_QUERY");
                    return item;
                }).toList();
        when(cases.selectList(any())).thenReturn(caseList);
        return new Fixture(runs, cases, results, run);
    }

    private record Fixture(AgentEvaluationRunMapper runs, AgentEvaluationCaseMapper cases,
                           AgentEvaluationResultMapper results, AgentEvaluationRunEntity run) {
        AgentEvaluationRunner runner(AgentEvaluationCaseExecutor executor) {
            return new AgentEvaluationRunner(runs, cases, results, executor,
                    new AgentEvaluationScorer(), new ObjectMapper());
        }
    }
}
