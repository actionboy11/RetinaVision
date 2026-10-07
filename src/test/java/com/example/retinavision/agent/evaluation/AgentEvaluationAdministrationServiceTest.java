package com.example.retinavision.agent.evaluation;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.mapper.*;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AgentEvaluationAdministrationServiceTest {

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
        AgentEvaluationAdministrationService service = new AgentEvaluationAdministrationService(
                mock(AgentEvaluationDatasetMapper.class), runs, bindings,
                mock(AgentEvaluationResultMapper.class), mock(AgentEvaluationCaseMapper.class),
                mock(AgentSkillMapper.class), mock(AgentSkillVersionMapper.class),
                mock(PromptTemplateMapper.class), mock(PromptTemplateVersionMapper.class),
                new LlmProperties(), new ObjectMapper());
        return new Fixture(service, runs, bindings);
    }

    private AgentEvaluationRunEntity run(String status, boolean pass) {
        AgentEvaluationRunEntity run = new AgentEvaluationRunEntity();
        run.setId(9L); run.setStatus(status); run.setAutomatedPass(pass);
        run.setCompletedCount(10); run.setTotalCount(10); run.setReviewDecision("PENDING");
        return run;
    }

    private record Fixture(AgentEvaluationAdministrationService service,
                           AgentEvaluationRunMapper runs,
                           AgentEvaluationRunBindingMapper bindings) {
    }
}
