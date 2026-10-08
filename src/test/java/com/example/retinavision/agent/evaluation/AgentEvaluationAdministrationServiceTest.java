package com.example.retinavision.agent.evaluation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.mapper.*;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import com.example.retinavision.pojo.VO.AgentEvaluationResultVO;
import com.example.retinavision.pojo.VO.AgentEvaluationRunVO;
import com.example.retinavision.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AgentEvaluationAdministrationServiceTest {

    @Test
    void runPaginationUsesAnExplicitCountWithoutMyBatisPaginationInterceptor() {
        Fixture fixture = fixture();
        when(fixture.runs.selectCount(any())).thenReturn(12L);
        when(fixture.runs.selectList(any())).thenReturn(List.of());
        when(fixture.runs.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        PageResult<AgentEvaluationRunVO> page = fixture.service.listRuns(null, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(12L);
        assertThat(page.getPageSize()).isEqualTo(10);
    }

    @Test
    void resultPaginationUsesAnExplicitCountWithoutMyBatisPaginationInterceptor() {
        Fixture fixture = fixture();
        when(fixture.runs.selectById(9L)).thenReturn(run("FAILED", false));
        when(fixture.results.selectCount(any())).thenReturn(5L);
        when(fixture.results.selectList(any())).thenReturn(List.of());
        when(fixture.results.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

        PageResult<AgentEvaluationResultVO> page = fixture.service.listResults(9L, false, null, 1, 20);

        assertThat(page.getTotal()).isEqualTo(5L);
        assertThat(page.getPageSize()).isEqualTo(20);
    }

    @Test
    void approvalRequiresAutomaticallyPassedRun() {
        Fixture fixture = fixture();
        AgentEvaluationRunEntity run = run("FAILED", false);
        when(fixture.runs.selectById(9L)).thenReturn(run);

        assertThatThrownBy(() -> fixture.service.review(9L, "APPROVED", "", 1))
                .isInstanceOf(BaseException.class).hasMessageContaining("自动评测通过");
        verify(fixture.runs, never()).updateById(any(AgentEvaluationRunEntity.class));
    }

    @Test
    void approvedDecisionPersistsReviewerAndTimestamp() {
        Fixture fixture = fixture();
        AgentEvaluationRunEntity run = run("PASSED", true);
        when(fixture.runs.selectById(9L)).thenReturn(run);
        when(fixture.bindings.selectList(any())).thenReturn(java.util.List.of());

        fixture.service.review(9L, "APPROVED", "抽查通过", 7);

        assertThat(run.getReviewDecision()).isEqualTo("APPROVED");
        assertThat(run.getReviewedBy()).isEqualTo(7);
        assertThat(run.getReviewedAt()).isNotNull();
        verify(fixture.runs).updateById(run);
    }

    private Fixture fixture() {
        AgentEvaluationRunMapper runs = mock(AgentEvaluationRunMapper.class);
        AgentEvaluationRunBindingMapper bindings = mock(AgentEvaluationRunBindingMapper.class);
        AgentEvaluationResultMapper results = mock(AgentEvaluationResultMapper.class);
        AgentEvaluationAdministrationService service = new AgentEvaluationAdministrationService(
                mock(AgentEvaluationDatasetMapper.class), runs, bindings,
                results, mock(AgentEvaluationCaseMapper.class),
                mock(AgentSkillMapper.class), mock(AgentSkillVersionMapper.class),
                mock(PromptTemplateMapper.class), mock(PromptTemplateVersionMapper.class),
                new LlmProperties(), new ObjectMapper());
        return new Fixture(service, runs, bindings, results);
    }

    private AgentEvaluationRunEntity run(String status, boolean pass) {
        AgentEvaluationRunEntity run = new AgentEvaluationRunEntity();
        run.setId(9L); run.setStatus(status); run.setAutomatedPass(pass);
        run.setCompletedCount(10); run.setTotalCount(10); run.setReviewDecision("PENDING");
        return run;
    }

    private record Fixture(AgentEvaluationAdministrationService service,
                           AgentEvaluationRunMapper runs,
                           AgentEvaluationRunBindingMapper bindings,
                           AgentEvaluationResultMapper results) {
    }
}
