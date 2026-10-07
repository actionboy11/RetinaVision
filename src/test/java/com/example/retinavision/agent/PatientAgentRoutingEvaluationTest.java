package com.example.retinavision.agent;

import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatientAgentRoutingEvaluationTest {
    private static final double MIN_ROUTING_ACCURACY = 0.90;
    private static final double MIN_PARAMETER_ACCURACY = 0.95;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void patientRoutingCorpusMeetsAccuracyThresholds() throws Exception {
        List<RoutingCase> corpus = List.of(
                route("我有哪些检查", AgentSkillCode.MY_CASE_LIST, Map.of()),
                route("列出我的检查申请", AgentSkillCode.MY_CASE_LIST, Map.of()),
                route("哪些图像需要重新上传", AgentSkillCode.MY_CASE_LIST,
                        Map.of("reuploadOnly", "TRUE")),
                route("只看已有正式报告的检查", AgentSkillCode.MY_CASE_LIST,
                        Map.of("signedReportOnly", "TRUE")),
                route("我最近一次检查到哪一步了", AgentSkillCode.MY_CASE_PROGRESS, Map.of()),
                route("病例 C-20261007-001 处理完了吗", AgentSkillCode.MY_CASE_PROGRESS,
                        Map.of("caseReference", "C-20261007-001")),
                route("查看 C-20261007-002 的检查进度", AgentSkillCode.MY_CASE_PROGRESS,
                        Map.of("caseReference", "C-20261007-002")),
                route("查看我的正式报告", AgentSkillCode.MY_SIGNED_REPORT, Map.of("mode", "LIST")),
                route("查看病例 C-20261007-001 的正式报告", AgentSkillCode.MY_SIGNED_REPORT,
                        Map.of("caseReference", "C-20261007-001", "mode", "VIEW")),
                route("解释我最新的正式报告", AgentSkillCode.MY_SIGNED_REPORT,
                        Map.of("mode", "EXPLAIN")),
                route("眼底图像为什么需要质量检查", AgentSkillCode.PATIENT_KNOWLEDGE_QA, Map.of()),
                route("血管分割是什么意思", AgentSkillCode.PATIENT_KNOWLEDGE_QA, Map.of()),
                route("散瞳检查通常有什么作用", AgentSkillCode.PATIENT_KNOWLEDGE_QA, Map.of())
        );

        int routingMatches = 0;
        int parameterMatches = 0;
        for (RoutingCase item : corpus) {
            LlmOrchestrationService llm = mock(LlmOrchestrationService.class);
            when(llm.generateJson(eq("AGENT_SKILL_ROUTER"), org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(generation(item));
            LlmAgentSkillRouter router = new LlmAgentSkillRouter(llm, json, new AgentSkillRegistry());

            AgentSkillRoute actual = router.route(item.question(), null, definitions());
            if (actual.skillCode() == item.skill()) routingMatches++;
            if (actual.arguments().entrySet().containsAll(item.arguments().entrySet())) parameterMatches++;
        }

        double routingAccuracy = (double) routingMatches / corpus.size();
        double parameterAccuracy = (double) parameterMatches / corpus.size();
        System.out.printf("Patient Agent routing accuracy: %.2f%%, parameter accuracy: %.2f%%%n",
                routingAccuracy * 100, parameterAccuracy * 100);
        assertThat(routingAccuracy).isGreaterThanOrEqualTo(MIN_ROUTING_ACCURACY);
        assertThat(parameterAccuracy).isGreaterThanOrEqualTo(MIN_PARAMETER_ACCURACY);
    }

    @Test
    void deterministicContextCorpusExtractsPaginationSelectionAndReportMode() {
        PatientAgentContextCommandParser parser = new PatientAgentContextCommandParser();
        PatientAgentQueryContextSnapshot cases = context(AgentSkillCode.MY_CASE_LIST);
        PatientAgentQueryContextSnapshot reports = context(AgentSkillCode.MY_SIGNED_REPORT);

        List<Boolean> matches = new ArrayList<>();
        matches.add(parser.parse("继续", cases).orElseThrow().command() == AgentContextCommand.NEXT_PAGE);
        matches.add(parser.parse("上一页", cases).orElseThrow().command() == AgentContextCommand.PREVIOUS_PAGE);
        AgentSkillRoute selectedCase = parser.parse("查看第二个", cases).orElseThrow();
        matches.add(selectedCase.skillCode() == AgentSkillCode.MY_CASE_PROGRESS
                && Integer.valueOf(2).equals(selectedCase.selectedIndex()));
        AgentSkillRoute selectedReport = parser.parse("查看第一个报告", reports).orElseThrow();
        matches.add(selectedReport.skillCode() == AgentSkillCode.MY_SIGNED_REPORT
                && "VIEW".equals(selectedReport.arguments().get("mode")));
        matches.add("EXPLAIN".equals(parser.parse("解释这份报告", reports).orElseThrow()
                .arguments().get("mode")));
        matches.add("TRUE".equals(parser.parse("只看需要重新上传的", cases).orElseThrow()
                .arguments().get("reuploadOnly")));

        double accuracy = matches.stream().filter(Boolean::booleanValue).count() / (double) matches.size();
        System.out.printf("Patient Agent deterministic parameter accuracy: %.2f%%%n", accuracy * 100);
        assertThat(accuracy).isGreaterThanOrEqualTo(MIN_PARAMETER_ACCURACY);
    }

    private RoutingCase route(String question, AgentSkillCode skill, Map<String, String> arguments) {
        return new RoutingCase(question, skill, arguments, 0.98);
    }

    private LlmGenerationResult generation(RoutingCase item) throws Exception {
        return new LlmGenerationResult(json.writeValueAsString(Map.of(
                "skillCode", item.skill().name(),
                "confidence", item.confidence(),
                "arguments", item.arguments())),
                "AGENT_SKILL_ROUTER", 1, "evaluation", "mock-router", 1);
    }

    private List<AgentSkillDefinition> definitions() {
        return List.of(
                definition(AgentSkillCode.MY_CASE_LIST, "我的检查列表"),
                definition(AgentSkillCode.MY_CASE_PROGRESS, "我的检查进度"),
                definition(AgentSkillCode.MY_SIGNED_REPORT, "我的正式报告"),
                definition(AgentSkillCode.PATIENT_KNOWLEDGE_QA, "患者医学知识")
        );
    }

    private AgentSkillDefinition definition(AgentSkillCode code, String name) {
        return new AgentSkillDefinition(code, name, name, 1, "[]", "只读执行");
    }

    private PatientAgentQueryContextSnapshot context(AgentSkillCode skill) {
        return new PatientAgentQueryContextSnapshot(skill, false, false, 1, 10, 2,
                1L, 101L, 1, List.of(
                new PatientAgentReference(1L, "C-1", 101L, 1),
                new PatientAgentReference(2L, "C-2", 102L, 1)));
    }

    private record RoutingCase(String question, AgentSkillCode skill,
                               Map<String, String> arguments, double confidence) {
    }
}
