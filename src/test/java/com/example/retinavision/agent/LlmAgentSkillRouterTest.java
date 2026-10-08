package com.example.retinavision.agent;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmAgentSkillRouterTest {

    @Test
    void sendsQuestionAndSkillCatalogWithoutToolSchemas() {
        LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
        when(llm.generateJson(eq("AGENT_SKILL_ROUTER"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result("{\"skillCode\":\"DOCTOR_TASK_SEARCH\",\"confidence\":0.96,"
                        + "\"arguments\":{\"status\":\"FAILED\"}}"));
        LlmAgentSkillRouter router = new LlmAgentSkillRouter(llm, new ObjectMapper(), new AgentSkillRegistry());

        AgentSkillRoute route = router.route("哪些分割任务失败了", null, List.of(taskDefinition()));

        assertThat(route.skillCode()).isEqualTo(AgentSkillCode.DOCTOR_TASK_SEARCH);
        assertThat(route.arguments())
                .containsEntry("taskType", "VESSEL_SEGMENTATION")
                .containsEntry("status", "FAILED");
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(llm).generateJson(eq("AGENT_SKILL_ROUTER"), context.capture());
        assertThat(context.getValue())
                .contains("哪些分割任务失败了", "DOCTOR_TASK_SEARCH", "routingExamples")
                .doesNotContain("ToolCallback", "toolSchemas", "searchMedicalKnowledge");
    }

    @Test
    void acceptsExplicitImageQualityTaskType() {
        LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
        when(llm.generateJson(eq("AGENT_SKILL_ROUTER"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result("{\"skillCode\":\"DOCTOR_TASK_SEARCH\",\"confidence\":0.9,"
                        + "\"arguments\":{\"taskType\":\"IMAGE_QUALITY_CHECK\"}}"));
        LlmAgentSkillRouter router = new LlmAgentSkillRouter(llm, new ObjectMapper(), new AgentSkillRegistry());

        AgentSkillRoute route = router.route("查询图像质检任务", null, List.of(taskDefinition()));

        assertThat(route.arguments()).containsEntry("taskType", "IMAGE_QUALITY_CHECK");
    }

    @Test
    void preservesExtractedCaseReferenceForDoctorCaseSkill() {
        LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
        when(llm.generateJson(eq("AGENT_SKILL_ROUTER"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result("{\"skillCode\":\"CASE_CLINICAL_SUMMARY\",\"confidence\":0.97,"
                        + "\"arguments\":{\"caseReference\":\"EVAL-C-005\"}}"));
        LlmAgentSkillRouter router = new LlmAgentSkillRouter(llm, new ObjectMapper(), new AgentSkillRegistry());
        AgentSkillDefinition definition = new AgentSkillDefinition(AgentSkillCode.CASE_CLINICAL_SUMMARY,
                "病例摘要", "查询病例摘要", 4, "[]", "只读查询病例摘要");

        AgentSkillRoute route = router.route("查看病例 EVAL-C-005 的摘要", null, List.of(definition));

        assertThat(route.arguments()).containsExactlyEntriesOf(Map.of("caseReference", "EVAL-C-005"));
    }

    @Test
    void returnsLowConfidenceRouteForCallerPolicyAndEvaluation() {
        LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
        when(llm.generateJson(eq("AGENT_SKILL_ROUTER"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result("{\"skillCode\":\"DOCTOR_TASK_SEARCH\",\"confidence\":0.2,\"arguments\":{}}"));
        LlmAgentSkillRouter router = new LlmAgentSkillRouter(llm, new ObjectMapper(), new AgentSkillRegistry());

        AgentSkillRoute route = router.route("重试失败任务", null, List.of(taskDefinition()));

        assertThat(route.skillCode()).isEqualTo(AgentSkillCode.DOCTOR_TASK_SEARCH);
        assertThat(route.confidence()).isEqualTo(0.2);
    }

    @Test
    void rejectsMalformedUnknownAndUnavailableRoutes() {
        assertRejected("not-json", List.of(taskDefinition()), "格式");
        assertRejected("{\"skillCode\":\"UNKNOWN\",\"confidence\":0.9,\"arguments\":{}}",
                List.of(taskDefinition()), "Skill");
        assertRejected("{\"skillCode\":\"DOCTOR_CLINICAL_QUEUE\",\"confidence\":0.9,\"arguments\":{}}",
                List.of(taskDefinition()), "不可用");
    }

    @Test
    void rejectsOutOfRangeConfidenceInsteadOfClampingIt() {
        assertRejected("{\"skillCode\":\"DOCTOR_TASK_SEARCH\",\"confidence\":2,\"arguments\":{}}",
                List.of(taskDefinition()), "置信度");
        assertRejected("{\"skillCode\":\"DOCTOR_TASK_SEARCH\",\"confidence\":-0.1,\"arguments\":{}}",
                List.of(taskDefinition()), "置信度");
    }

    private void assertRejected(String json, List<AgentSkillDefinition> definitions, String message) {
        LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
        when(llm.generateJson(eq("AGENT_SKILL_ROUTER"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result(json));
        LlmAgentSkillRouter router = new LlmAgentSkillRouter(llm, new ObjectMapper(), new AgentSkillRegistry());

        assertThatThrownBy(() -> router.route("测试问题", null, definitions))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining(message);
    }

    private AgentSkillDefinition taskDefinition() {
        return new AgentSkillDefinition(AgentSkillCode.DOCTOR_TASK_SEARCH, "医生任务查询", "查询医生任务", 1,
                "[{\"query\":\"查询我的任务\"}]", "执行任务查询");
    }

    private LlmGenerationResult result(String content) {
        return new LlmGenerationResult(content, "AGENT_SKILL_ROUTER", 1, "test", "test", 1);
    }
}
