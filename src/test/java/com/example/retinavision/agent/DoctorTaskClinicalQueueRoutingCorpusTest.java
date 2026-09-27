package com.example.retinavision.agent;

import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoctorTaskClinicalQueueRoutingCorpusTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptanceCorpus")
    void routesTaskAndClinicalQueueAcceptancePhrases(Case item) {
        LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
        when(llm.generateJson(anyString(), anyString())).thenReturn(generation(item.response()));
        LlmAgentSkillRouter router = new LlmAgentSkillRouter(llm, new ObjectMapper(), new AgentSkillRegistry());

        AgentSkillRoute route = router.route(item.query(), null, definitions());

        assertThat(route.skillCode()).isEqualTo(item.skill());
        assertThat(route.arguments()).containsAllEntriesOf(item.arguments());
    }

    @ParameterizedTest(name = "negative: {0}")
    @MethodSource("negativeCorpus")
    void ambiguousAndWriteRequestsDoNotRouteToProtectedReadSkills(Case item) {
        LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
        when(llm.generateJson(anyString(), anyString())).thenReturn(generation(item.response()));
        LlmAgentSkillRouter router = new LlmAgentSkillRouter(llm, new ObjectMapper(), new AgentSkillRegistry());

        AgentSkillRoute route = router.route(item.query(), null, definitions());

        assertThat(route.skillCode()).isEqualTo(AgentSkillCode.MEDICAL_KNOWLEDGE_QA);
    }

    static Stream<Case> acceptanceCorpus() {
        return Stream.of(
                task("查询我的任务", "VESSEL_SEGMENTATION", "ANY", "ANY"),
                task("哪些分割任务失败了", "VESSEL_SEGMENTATION", "FAILED", "ANY"),
                task("最近七天有哪些正在处理的任务", "VESSEL_SEGMENTATION", "RUNNING", "LAST_7_DAYS"),
                new Case("查看任务 T202609270001", AgentSkillCode.DOCTOR_TASK_SEARCH,
                        Map.of("taskReference", "T202609270001"), json("DOCTOR_TASK_SEARCH", 0.98,
                        Map.of("taskReference", "T202609270001"))),
                task("查询图像质检任务", "IMAGE_QUALITY_CHECK", "ANY", "ANY"),
                queue("今天有哪些待审核结果", "PENDING_REVIEW", "TODAY"),
                queue("哪些结果已经审核但还没有签发", "PENDING_REPORT", "ANY"),
                queue("列出最近七天待签发报告", "PENDING_REPORT", "LAST_7_DAYS")
        );
    }

    static Stream<Case> negativeCorpus() {
        return Stream.of(
                knowledge("病例任务是什么"),
                knowledge("帮我创建一个血管分割任务"),
                knowledge("删除失败任务"),
                knowledge("审核并签发第一份报告")
        );
    }

    private static List<AgentSkillDefinition> definitions() {
        return List.of(
                new AgentSkillDefinition(AgentSkillCode.DOCTOR_TASK_SEARCH, "任务查询", "只读任务查询", 1, "[]", "只读"),
                new AgentSkillDefinition(AgentSkillCode.DOCTOR_CLINICAL_QUEUE, "临床队列", "只读临床待办", 1, "[]", "只读"),
                new AgentSkillDefinition(AgentSkillCode.MEDICAL_KNOWLEDGE_QA, "医学知识", "知识解释", 1, "[]", "只读")
        );
    }

    private static Case task(String query, String type, String status, String dateWindow) {
        Map<String, String> args = Map.of("taskType", type, "status", status, "dateWindow", dateWindow);
        return new Case(query, AgentSkillCode.DOCTOR_TASK_SEARCH, args,
                json("DOCTOR_TASK_SEARCH", 0.98, args));
    }

    private static Case queue(String query, String type, String dateWindow) {
        Map<String, String> args = Map.of("queueType", type, "dateWindow", dateWindow);
        return new Case(query, AgentSkillCode.DOCTOR_CLINICAL_QUEUE, args,
                json("DOCTOR_CLINICAL_QUEUE", 0.98, args));
    }

    private static Case knowledge(String query) {
        return new Case(query, AgentSkillCode.MEDICAL_KNOWLEDGE_QA, Map.of(),
                json("MEDICAL_KNOWLEDGE_QA", 0.9, Map.of()));
    }

    private static String json(String skill, double confidence, Map<String, String> arguments) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                    "skillCode", skill, "confidence", confidence, "arguments", arguments));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static LlmGenerationResult generation(String content) {
        return new LlmGenerationResult(content, "AGENT_SKILL_ROUTER", 1, "qwen", "qwen-plus", 1);
    }

    record Case(String query, AgentSkillCode skill, Map<String, String> arguments, String response) {
        @Override public String toString() { return query; }
    }
}
