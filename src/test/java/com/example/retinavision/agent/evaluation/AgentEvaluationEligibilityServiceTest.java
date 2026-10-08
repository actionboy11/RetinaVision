package com.example.retinavision.agent.evaluation;

import com.example.retinavision.agent.AgentSkillRegistry;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.mapper.*;
import com.example.retinavision.pojo.Entity.AgentEvaluationDatasetEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunBindingEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateEntity;
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
        List<AgentEvaluationRunBindingEntity> snapshot = List.of(
                binding(5L, "SKILL", "ASSIGNED_CASE_SEARCH", 31L),
                binding(5L, "PROMPT", "AGENT_SKILL_ROUTER", 88L));
        when(fixture.bindings.selectList(any())).thenReturn(snapshot, snapshot);
        when(fixture.runs.selectById(5L)).thenReturn(run("DOCTOR", "qwen", "qwen-plus", "APPROVED"));
        stubCurrentConfiguration(fixture, "DOCTOR", 7L, 1, 88L,
                List.of(skill("ASSIGNED_CASE_SEARCH", 30L)));

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
        List<AgentEvaluationRunBindingEntity> candidates = List.of(
                binding(5L, "PROMPT", "AGENT_SKILL_ROUTER", 88L),
                binding(6L, "PROMPT", "AGENT_SKILL_ROUTER", 88L));
        List<AgentEvaluationRunBindingEntity> doctorSnapshot = List.of(
                candidates.get(0), binding(5L, "SKILL", "ASSIGNED_CASE_SEARCH", 31L));
        List<AgentEvaluationRunBindingEntity> patientSnapshot = List.of(
                candidates.get(1), binding(6L, "SKILL", "MY_CASE_LIST", 41L));
        when(fixture.bindings.selectList(any())).thenReturn(candidates, doctorSnapshot, patientSnapshot);
        when(fixture.runs.selectById(5L)).thenReturn(run("DOCTOR", "qwen", "qwen-plus", "APPROVED"));
        when(fixture.runs.selectById(6L)).thenReturn(run("PATIENT", "qwen", "qwen-plus", "APPROVED"));
        when(fixture.datasets.selectOne(any())).thenReturn(
                dataset("DOCTOR", 7L, 1), dataset("PATIENT", 8L, 1));
        when(fixture.skills.selectList(any())).thenReturn(
                List.of(skill("ASSIGNED_CASE_SEARCH", 31L)),
                List.of(skill("MY_CASE_LIST", 41L)));
        when(fixture.prompts.selectOne(any())).thenReturn(prompt(77L));
        AgentEvaluationRunEntity patientRun = run("PATIENT", "qwen", "qwen-plus", "APPROVED");
        patientRun.setId(6L); patientRun.setDatasetId(8L);
        when(fixture.runs.selectById(6L)).thenReturn(patientRun);

        assertThatCode(() -> fixture.service.requirePromptEligible("AGENT_SKILL_ROUTER", 88L))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsApprovedRunFromAnOlderDatasetVersion() {
        Fixture fixture = fixture();
        List<AgentEvaluationRunBindingEntity> snapshot = List.of(
                binding(5L, "SKILL", "ASSIGNED_CASE_SEARCH", 31L),
                binding(5L, "PROMPT", "AGENT_SKILL_ROUTER", 88L));
        when(fixture.bindings.selectList(any())).thenReturn(snapshot, snapshot);
        when(fixture.runs.selectById(5L)).thenReturn(run("DOCTOR", "qwen", "qwen-plus", "APPROVED"));
        stubCurrentConfiguration(fixture, "DOCTOR", 7L, 2, 88L,
                List.of(skill("ASSIGNED_CASE_SEARCH", 30L)));

        assertThatThrownBy(() -> fixture.service.requireSkillEligible("ASSIGNED_CASE_SEARCH", 31L))
                .isInstanceOf(BaseException.class).hasMessageContaining("评测");
    }

    @Test
    void rejectsSkillRunWhoseRouterPromptIsNoLongerActive() {
        Fixture fixture = fixture();
        List<AgentEvaluationRunBindingEntity> snapshot = List.of(
                binding(5L, "SKILL", "ASSIGNED_CASE_SEARCH", 31L),
                binding(5L, "PROMPT", "AGENT_SKILL_ROUTER", 88L));
        when(fixture.bindings.selectList(any())).thenReturn(snapshot, snapshot);
        when(fixture.runs.selectById(5L)).thenReturn(run("DOCTOR", "qwen", "qwen-plus", "APPROVED"));
        stubCurrentConfiguration(fixture, "DOCTOR", 7L, 1, 99L,
                List.of(skill("ASSIGNED_CASE_SEARCH", 30L)));

        assertThatThrownBy(() -> fixture.service.requireSkillEligible("ASSIGNED_CASE_SEARCH", 31L))
                .isInstanceOf(BaseException.class).hasMessageContaining("评测");
    }

    private Fixture fixture() {
        AgentEvaluationRunMapper runs = mock(AgentEvaluationRunMapper.class);
        AgentEvaluationRunBindingMapper bindings = mock(AgentEvaluationRunBindingMapper.class);
        AgentEvaluationDatasetMapper datasets = mock(AgentEvaluationDatasetMapper.class);
        AgentSkillMapper skills = mock(AgentSkillMapper.class);
        PromptTemplateMapper prompts = mock(PromptTemplateMapper.class);
        LlmProperties llm = new LlmProperties(); llm.setProvider("qwen"); llm.setModel("qwen-plus");
        return new Fixture(new AgentEvaluationEligibilityService(runs, bindings, datasets, skills,
                prompts, new AgentSkillRegistry(), llm), runs, bindings, datasets, skills, prompts);
    }

    private AgentEvaluationRunEntity run(String role, String provider, String model, String review) {
        AgentEvaluationRunEntity run = new AgentEvaluationRunEntity();
        run.setId(5L); run.setTargetRole(role); run.setProvider(provider); run.setModel(model);
        run.setDatasetId(7L); run.setDatasetVersion(1);
        run.setStatus("PASSED"); run.setAutomatedPass(true); run.setReviewDecision(review);
        return run;
    }

    private void stubCurrentConfiguration(Fixture fixture, String role, Long datasetId,
                                          int datasetVersion, Long promptVersion,
                                          List<AgentSkillEntity> activeSkills) {
        when(fixture.datasets.selectOne(any())).thenReturn(dataset(role, datasetId, datasetVersion));
        when(fixture.skills.selectList(any())).thenReturn(activeSkills);
        when(fixture.prompts.selectOne(any())).thenReturn(prompt(promptVersion));
    }

    private AgentEvaluationDatasetEntity dataset(String role, Long id, int version) {
        AgentEvaluationDatasetEntity value = new AgentEvaluationDatasetEntity();
        value.setId(id); value.setTargetRole(role); value.setVersion(version); value.setStatus("ACTIVE");
        return value;
    }

    private AgentSkillEntity skill(String code, Long activeVersionId) {
        AgentSkillEntity value = new AgentSkillEntity();
        value.setSkillCode(code); value.setActiveVersionId(activeVersionId); value.setStatus("ACTIVE");
        return value;
    }

    private PromptTemplateEntity prompt(Long activeVersionId) {
        PromptTemplateEntity value = new PromptTemplateEntity();
        value.setTemplateCode("AGENT_SKILL_ROUTER"); value.setActiveVersionId(activeVersionId);
        return value;
    }

    private AgentEvaluationRunBindingEntity binding(Long runId, String type, String code, Long versionId) {
        AgentEvaluationRunBindingEntity value = new AgentEvaluationRunBindingEntity();
        value.setEvaluationRunId(runId); value.setBindingType(type); value.setBindingCode(code);
        value.setVersionId(versionId);
        return value;
    }

    private record Fixture(AgentEvaluationEligibilityService service, AgentEvaluationRunMapper runs,
                           AgentEvaluationRunBindingMapper bindings,
                           AgentEvaluationDatasetMapper datasets, AgentSkillMapper skills,
                           PromptTemplateMapper prompts) {}
}
