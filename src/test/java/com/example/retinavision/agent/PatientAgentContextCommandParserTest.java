package com.example.retinavision.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatientAgentContextCommandParserTest {

    private final PatientAgentContextCommandParser parser = new PatientAgentContextCommandParser();

    @Test
    void parsesPaginationAndCaseSelectionFromCurrentPage() {
        var context = context(AgentSkillCode.MY_CASE_LIST);

        assertThat(parser.parse("继续", context)).get().extracting(AgentSkillRoute::command)
                .isEqualTo(AgentContextCommand.NEXT_PAGE);
        assertThat(parser.parse("上一页", context)).get().extracting(AgentSkillRoute::command)
                .isEqualTo(AgentContextCommand.PREVIOUS_PAGE);
        assertThat(parser.parse("查看第二个", context)).get().satisfies(route -> {
            assertThat(route.skillCode()).isEqualTo(AgentSkillCode.MY_CASE_PROGRESS);
            assertThat(route.command()).isEqualTo(AgentContextCommand.SELECT_INDEX);
            assertThat(route.selectedIndex()).isEqualTo(2);
        });
    }

    @Test
    void parsesReportViewExplanationAndReuploadFilterDeterministically() {
        var report = context(AgentSkillCode.MY_SIGNED_REPORT);

        assertThat(parser.parse("查看第一个报告", report)).get().satisfies(route -> {
            assertThat(route.skillCode()).isEqualTo(AgentSkillCode.MY_SIGNED_REPORT);
            assertThat(route.arguments()).containsEntry("mode", "VIEW");
        });
        assertThat(parser.parse("解释这份报告", report)).get().satisfies(route ->
                assertThat(route.arguments()).containsEntry("mode", "EXPLAIN"));
        assertThat(parser.parse("只看需要重新上传的", context(AgentSkillCode.MY_CASE_LIST))).get()
                .extracting(AgentSkillRoute::arguments)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("reuploadOnly", "TRUE");
    }

    private PatientAgentQueryContextSnapshot context(AgentSkillCode skill) {
        return new PatientAgentQueryContextSnapshot(skill, false, false, 1, 10, 2,
                1L, 101L, 1, List.of(
                new PatientAgentReference(1L, "C-1", 101L, 1),
                new PatientAgentReference(2L, "C-2", 102L, 1)));
    }
}
