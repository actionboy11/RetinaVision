package com.example.retinavision.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorAgentRoutingEvaluationTest {
    private final DefaultAgentSkillRouter router = new DefaultAgentSkillRouter();

    @Test
    void corpusContainsFiftyRepresentativeDoctorQuestions() {
        assertThat(corpus()).hasSize(50);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void routesDoctorLanguageWithExpectedParameters(EvaluationCase item) {
        AgentSkillRoute route = router.route(item.query(), item.currentSkill());

        assertThat(route.skillCode()).as(item.query()).isEqualTo(item.expectedSkill());
        assertThat(route.arguments()).as(item.query()).containsAllEntriesOf(item.expectedArguments());
        assertThat(route.command()).as(item.query()).isEqualTo(item.expectedCommand());
        assertThat(route.selectedIndex()).as(item.query()).isEqualTo(item.selectedIndex());
    }

    static Stream<EvaluationCase> corpus() {
        return Stream.of(
                item("我有多少名患者", AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW),
                item("我负责多少个病例", AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW),
                item("看一下我的工作量", AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW),
                item("名下患者有多少", AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW),
                item("现在管着几个病人", AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW),
                item("我手头有多少病例", AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW),
                item("给我一个工作量总览", AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW),
                item("我的患者总数是多少", AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW),

                search("查询我负责的病例", Map.of("segmentationState", "ANY")),
                search("把我负责的患者列出来", Map.of("segmentationState", "ANY")),
                search("列出我的病例", Map.of("segmentationState", "ANY")),
                search("哪些病例还没有进行分割", Map.of("segmentationState", "NOT_CREATED")),
                search("没跑血管分割的病例", Map.of("segmentationState", "NOT_CREATED")),
                search("还没做血管分割的患者", Map.of("segmentationState", "NOT_CREATED")),
                search("哪些病例的分割还没完成", Map.of("segmentationState", "NOT_COMPLETED")),
                search("分割正在处理的病例", Map.of("segmentationState", "IN_PROGRESS")),
                search("正在分割的病例", Map.of("segmentationState", "IN_PROGRESS")),
                search("分割失败的病例", Map.of("segmentationState", "FAILED")),
                search("最近有哪些任务失败", Map.of("segmentationState", "FAILED")),
                search("分割成功的病例", Map.of("segmentationState", "SUCCESS")),
                search("最近一个月右眼分割失败的病例", Map.of(
                        "segmentationState", "FAILED", "dateWindow", "LAST_30_DAYS", "eyeSide", "RIGHT")),
                search("最近七天左眼病例", Map.of(
                        "segmentationState", "ANY", "dateWindow", "LAST_7_DAYS", "eyeSide", "LEFT")),
                search("今天的双眼病例", Map.of(
                        "segmentationState", "ANY", "dateWindow", "TODAY", "eyeSide", "BOTH")),
                search("今天有哪些待审核结果", Map.of(
                        "segmentationState", "ANY", "clinicalState", "PENDING_REVIEW", "dateWindow", "TODAY")),
                search("列出待审核病例", Map.of(
                        "segmentationState", "ANY", "clinicalState", "PENDING_REVIEW")),
                search("哪些结果已经审核但还没有签发", Map.of(
                        "segmentationState", "ANY", "clinicalState", "PENDING_REPORT")),
                search("已审核但PDF还没出的病例", Map.of(
                        "segmentationState", "ANY", "clinicalState", "PENDING_REPORT")),
                command("继续", AgentContextCommand.NEXT_PAGE),
                command("上一页", AgentContextCommand.PREVIOUS_PAGE),
                command("只看失败的", AgentContextCommand.FILTER_FAILED),

                item("查看病例 C20260926083238176 的摘要", AgentSkillCode.CASE_CLINICAL_SUMMARY),
                item("查看匿名患者 PT-XE6V-93SR 的病例进度", AgentSkillCode.CASE_CLINICAL_SUMMARY),
                selection("查看第三个", 3),
                selection("查看第3个病例", 3),
                selection("打开第三个病例", 3),
                selection("看一下第二个病例", 2),
                selection("查看第十个", 10),

                item("比较它最近两次结果", AgentSkillCode.CASE_FOLLOWUP_ANALYSIS),
                item("查看这个病例的历史分析时间线", AgentSkillCode.CASE_FOLLOWUP_ANALYSIS),
                item("血管面积比例有什么变化", AgentSkillCode.CASE_FOLLOWUP_ANALYSIS),
                item("对比这个病例前后两次结果", AgentSkillCode.CASE_FOLLOWUP_ANALYSIS),
                item("这个病例的趋势怎么样", AgentSkillCode.CASE_FOLLOWUP_ANALYSIS),
                item("和上一次结果相比有什么变化", AgentSkillCode.CASE_FOLLOWUP_ANALYSIS),

                item("为什么要做视网膜血管分割", AgentSkillCode.MEDICAL_KNOWLEDGE_QA),
                item("血管面积占比是什么意思", AgentSkillCode.MEDICAL_KNOWLEDGE_QA),
                item("图像质量低有什么影响", AgentSkillCode.MEDICAL_KNOWLEDGE_QA),
                item("质量评分应该怎么理解", AgentSkillCode.MEDICAL_KNOWLEDGE_QA),
                item("什么是眼底血管分割", AgentSkillCode.MEDICAL_KNOWLEDGE_QA),
                item("FAIL评分代表什么", AgentSkillCode.MEDICAL_KNOWLEDGE_QA),
                item("血管分割有什么作用", AgentSkillCode.MEDICAL_KNOWLEDGE_QA)
        );
    }

    private static EvaluationCase item(String query, AgentSkillCode skill) {
        return new EvaluationCase(query, null, skill, Map.of(), AgentContextCommand.NONE, null);
    }

    private static EvaluationCase search(String query, Map<String, String> arguments) {
        return new EvaluationCase(query, null, AgentSkillCode.ASSIGNED_CASE_SEARCH, arguments,
                AgentContextCommand.NONE, null);
    }

    private static EvaluationCase command(String query, AgentContextCommand command) {
        return new EvaluationCase(query, AgentSkillCode.ASSIGNED_CASE_SEARCH,
                AgentSkillCode.ASSIGNED_CASE_SEARCH, Map.of(), command, null);
    }

    private static EvaluationCase selection(String query, int index) {
        return new EvaluationCase(query, AgentSkillCode.ASSIGNED_CASE_SEARCH,
                AgentSkillCode.CASE_CLINICAL_SUMMARY, Map.of(), AgentContextCommand.SELECT_INDEX, index);
    }

    record EvaluationCase(String query, AgentSkillCode currentSkill, AgentSkillCode expectedSkill,
                          Map<String, String> expectedArguments, AgentContextCommand expectedCommand,
                          Integer selectedIndex) {
        @Override
        public String toString() {
            return query;
        }
    }
}
