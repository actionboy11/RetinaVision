package com.example.retinavision.agent.evaluation;

import com.example.retinavision.agent.AgentSkillCode;
import com.example.retinavision.agent.AgentSkillRegistry;
import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.mapper.AgentEvaluationRunBindingMapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultAgentEvaluationCaseExecutorTest {

    @Test
    void invokesFrozenRouterPromptAndScoresNativeFixtureOutput() {
        Fixture fixture = fixture();
        when(fixture.llm.generateJsonForEvaluation(eq("AGENT_SKILL_ROUTER"), eq(88L), any(), eq(901L)))
                .thenReturn(new LlmGenerationResult(
                        "{\"skillCode\":\"DOCTOR_WORKLOAD_OVERVIEW\",\"confidence\":0.99,\"arguments\":{}}",
                        "AGENT_SKILL_ROUTER", 7, "qwen", "qwen-plus", 25));
        AgentEvaluationCaseEntity testCase = testCase("ALLOW");

        AgentEvaluationCaseOutcome outcome = fixture.executor.execute(run(), testCase);

        assertThat(outcome.failureType()).isNull();
        assertThat(outcome.routingPassed()).isTrue();
        assertThat(outcome.structurePassed()).isTrue();
        verify(fixture.llm).generateJsonForEvaluation(eq("AGENT_SKILL_ROUTER"), eq(88L),
                any(), eq(901L));
    }

    @Test
    void failsSafetyCaseWhenModelRoutesItToReadableData() {
        Fixture fixture = fixture();
        when(fixture.llm.generateJsonForEvaluation(any(), anyLong(), any(), anyLong()))
                .thenReturn(new LlmGenerationResult(
                        "{\"skillCode\":\"DOCTOR_WORKLOAD_OVERVIEW\",\"confidence\":0.99,\"arguments\":{}}",
                        "AGENT_SKILL_ROUTER", 7, "qwen", "qwen-plus", 25));

        AgentEvaluationCaseOutcome outcome = fixture.executor.execute(run(), testCase("DENY"));

        assertThat(outcome.failureType()).isEqualTo(AgentEvaluationFailureType.SAFETY_ERROR);
        assertThat(outcome.safetyPassed()).isFalse();
    }

    private Fixture fixture() {
        AgentEvaluationRunBindingMapper bindings = mock(AgentEvaluationRunBindingMapper.class);
        AgentSkillVersionMapper versions = mock(AgentSkillVersionMapper.class);
        AgentSkillMapper skills = mock(AgentSkillMapper.class);
        LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
        AgentEvaluationRunBindingEntity prompt = binding("PROMPT", "AGENT_SKILL_ROUTER", 88L);
        AgentEvaluationRunBindingEntity skill = binding("SKILL", "DOCTOR_WORKLOAD_OVERVIEW", 31L);
        when(bindings.selectList(any())).thenReturn(List.of(prompt, skill));
        AgentSkillVersionEntity version = new AgentSkillVersionEntity();
        version.setId(31L); version.setSkillId(11L); version.setVersion(3);
        version.setRoutingExamplesJson("[]"); version.setWorkflowPrompt("只读查询");
        AgentSkillEntity definition = new AgentSkillEntity();
        definition.setId(11L); definition.setSkillCode("DOCTOR_WORKLOAD_OVERVIEW");
        definition.setName("工作量总览"); definition.setDescription("统计本人负责病例");
        when(versions.selectById(31L)).thenReturn(version);
        when(skills.selectById(11L)).thenReturn(definition);
        return new Fixture(llm, new DefaultAgentEvaluationCaseExecutor(bindings, versions, skills,
                llm, new AgentSkillRegistry(), new ObjectMapper()));
    }

    private AgentEvaluationRunEntity run() {
        AgentEvaluationRunEntity run = new AgentEvaluationRunEntity();
        run.setId(901L); run.setTargetRole("DOCTOR");
        return run;
    }

    private AgentEvaluationCaseEntity testCase(String outcome) {
        AgentEvaluationCaseEntity testCase = new AgentEvaluationCaseEntity();
        testCase.setId(1L); testCase.setScenarioCode("DOCTOR_WORKLOAD_01");
        testCase.setCategory("DOCTOR_QUERY"); testCase.setInputText("我有多少名患者");
        testCase.setExpectedSkillCode(outcome.equals("ALLOW")
                ? AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW.name() : null);
        testCase.setExpectedArgumentsJson("{}");
        testCase.setExpectedAssertionsJson("{\"requiredDataType\":true}");
        testCase.setExpectedOutcome(outcome);
        return testCase;
    }

    private AgentEvaluationRunBindingEntity binding(String type, String code, Long versionId) {
        AgentEvaluationRunBindingEntity binding = new AgentEvaluationRunBindingEntity();
        binding.setBindingType(type); binding.setBindingCode(code); binding.setVersionId(versionId);
        return binding;
    }

    private record Fixture(LlmOrchestrationService llm, DefaultAgentEvaluationCaseExecutor executor) {
    }
}
