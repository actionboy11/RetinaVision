package com.example.retinavision.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentContextCommandParserTest {

    private final AgentContextCommandParser parser = new AgentContextCommandParser();

    @Test
    void parsesPaginationAndFilterCommandsWithoutModelRouting() {
        AgentQueryContextSnapshot context = context(AgentSkillCode.DOCTOR_TASK_SEARCH, AgentReferenceType.TASK);

        assertThat(parser.parse("继续", context).orElseThrow().command())
                .isEqualTo(AgentContextCommand.NEXT_PAGE);
        assertThat(parser.parse("上一页", context).orElseThrow().command())
                .isEqualTo(AgentContextCommand.PREVIOUS_PAGE);
        assertThat(parser.parse("只看失败的", context).orElseThrow().command())
                .isEqualTo(AgentContextCommand.FILTER_FAILED);
        assertThat(parser.parse("普通问题", context)).isEmpty();
    }

    @Test
    void resolvesSelectionAgainstCurrentReferenceType() {
        AgentSkillRoute task = parser.parse("查看第三个", context(
                AgentSkillCode.DOCTOR_TASK_SEARCH, AgentReferenceType.TASK)).orElseThrow();
        AgentSkillRoute queue = parser.parse("查看第二个", context(
                AgentSkillCode.DOCTOR_CLINICAL_QUEUE, AgentReferenceType.CLINICAL_QUEUE)).orElseThrow();
        AgentSkillRoute caseRoute = parser.parse("打开第一个", context(
                AgentSkillCode.ASSIGNED_CASE_SEARCH, AgentReferenceType.CASE)).orElseThrow();

        assertThat(task.skillCode()).isEqualTo(AgentSkillCode.DOCTOR_TASK_SEARCH);
        assertThat(task.selectedIndex()).isEqualTo(3);
        assertThat(queue.skillCode()).isEqualTo(AgentSkillCode.DOCTOR_CLINICAL_QUEUE);
        assertThat(queue.selectedIndex()).isEqualTo(2);
        assertThat(caseRoute.skillCode()).isEqualTo(AgentSkillCode.CASE_CLINICAL_SUMMARY);
        assertThat(caseRoute.selectedIndex()).isEqualTo(1);
    }

    private AgentQueryContextSnapshot context(AgentSkillCode skill, AgentReferenceType referenceType) {
        return new AgentQueryContextSnapshot(skill, referenceType, SegmentationState.ANY,
                DoctorClinicalState.ANY, DoctorDateWindow.ANY, null,
                1, 10, 2, null, null, List.of(1, 2));
    }
}
