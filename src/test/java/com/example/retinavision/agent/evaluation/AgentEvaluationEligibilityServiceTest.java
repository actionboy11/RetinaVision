package com.example.retinavision.agent.evaluation;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.mapper.AgentEvaluationRunBindingMapper;
import com.example.retinavision.mapper.AgentEvaluationRunMapper;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunBindingEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEvaluationEligibilityServiceTest {

    @Test
    void skillActivationRequiresApprovedPassingRunWithPromptSnapshotAndCurrentModel() {
        Fixture fixture = fixture();
        when(fixture.bindings.selectList(any())).thenReturn(List.of(
                binding(5L, "SKILL", "ASSIGNED_CASE_SEARCH", 31L),
                binding(5L, "PROMPT", "AGENT_SKILL_ROUTER", 88L)));
        when(fixture.runs.selectById(5L)).thenReturn(run("DOCTOR", "qwen", "qwen-plus", "APPROVED"));
        when(fixture.bindings.selectCount(any())).thenReturn(1L);

        assertThatCode(() -> fixture.service.requireSkillEligible(
                "ASSIGNED_CASE_SEARCH", 31L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsModelMismatchOrUnapprovedRun() {
        Fixture fixture = fixture();
        when(fixture.bindings.selectList(any())).thenReturn(List.of(
                binding(5L, "PROMPT", "AGENT_SKILL_ROUTER", 88L),
                binding(5L, "SKILL", "MY_CASE_LIST", 41L)));
        when(fixture.runs.selectById(5L)).thenReturn(run("PATIENT", "qwen", "old-model", "PENDING"));

        assertThatThrownBy(() -> fixture.service.requirePromptEligible("AGENT_SKILL_ROUTER", 88L))
                .isInstanceOf(BaseException.class).hasMessageContaining("评测");
    }

    @Test
    void routerPromptRequiresApprovedDoctorAndPatientRuns() {
        Fixture fixture = fixture();
        when(fixture.bindings.selectList(any())).thenReturn(List.of(
                binding(5L, "PROMPT", "AGENT_SKILL_ROUTER", 88L),
                binding(6L, "PROMPT", "AGENT_SKILL_ROUTER", 88L)));
        when(fixture.bindings.selectCount(any())).thenReturn(1L);
        when(fixture.runs.selectById(5L)).thenReturn(run("DOCTOR", "qwen", "qwen-plus", "APPROVED"));
        when(fixture.runs.selectById(6L)).thenReturn(run("PATIENT", "qwen", "qwen-plus", "APPROVED"));

        assertThatCode(() -> fixture.service.requirePromptEligible("AGENT_SKILL_ROUTER", 88L))
                .doesNotThrowAnyException();
    }

    private Fixture fixture() {
        AgentEvaluationRunMapper runs = mock(AgentEvaluationRunMapper.class);
        AgentEvaluationRunBindingMapper bindings = mock(AgentEvaluationRunBindingMapper.class);
        LlmProperties llm = new LlmProperties(); llm.setProvider("qwen"); llm.setModel("qwen-plus");
        return new Fixture(new AgentEvaluationEligibilityService(runs, bindings, llm), runs, bindings);
    }

    private AgentEvaluationRunEntity run(String role, String provider, String model, String review) {
        AgentEvaluationRunEntity run = new AgentEvaluationRunEntity();
        run.setId(5L); run.setTargetRole(role); run.setProvider(provider); run.setModel(model);
        run.setStatus("PASSED"); run.setAutomatedPass(true); run.setReviewDecision(review);
        return run;
    }

    private AgentEvaluationRunBindingEntity binding(Long runId, String type, String code, Long versionId) {
        AgentEvaluationRunBindingEntity value = new AgentEvaluationRunBindingEntity();
        value.setEvaluationRunId(runId); value.setBindingType(type); value.setBindingCode(code);
        value.setVersionId(versionId);
        return value;
    }

    private record Fixture(AgentEvaluationEligibilityService service, AgentEvaluationRunMapper runs,
                           AgentEvaluationRunBindingMapper bindings) {}
}
