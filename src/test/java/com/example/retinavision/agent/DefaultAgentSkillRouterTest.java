package com.example.retinavision.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentSkillRouterTest {
    private final DefaultAgentSkillRouter router = new DefaultAgentSkillRouter();

    @Test
    void routesDoctorWorkloadAndUnsegmentedSearch() {
        assertThat(router.route("我有多少名患者？", null).skillCode())
                .isEqualTo(AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW);
        AgentSkillRoute search = router.route("有哪些病例还没有进行分割？", null);
        assertThat(search.skillCode()).isEqualTo(AgentSkillCode.ASSIGNED_CASE_SEARCH);
        assertThat(search.arguments()).containsEntry("segmentationState", "NOT_CREATED");
    }

    @Test
    void distinguishesNotCreatedFromNotCompletedSegmentation() {
        assertThat(router.route("哪些病例还没做分割？", null).arguments())
                .containsEntry("segmentationState", "NOT_CREATED");
        assertThat(router.route("我有哪些病人还没有分割？", null).arguments())
                .containsEntry("segmentationState", "NOT_CREATED");
        assertThat(router.route("哪些病例的分割还没完成？", null).arguments())
                .containsEntry("segmentationState", "NOT_COMPLETED");
    }

    @Test
    void routesContextCommandsWithoutAskingModelToGuess() {
        assertThat(router.route("继续", AgentSkillCode.ASSIGNED_CASE_SEARCH).command())
                .isEqualTo(AgentContextCommand.NEXT_PAGE);
        assertThat(router.route("上一页", AgentSkillCode.ASSIGNED_CASE_SEARCH).command())
                .isEqualTo(AgentContextCommand.PREVIOUS_PAGE);
        assertThat(router.route("只看失败的", AgentSkillCode.ASSIGNED_CASE_SEARCH).command())
                .isEqualTo(AgentContextCommand.FILTER_FAILED);
        assertThat(router.route("查看第三个", AgentSkillCode.ASSIGNED_CASE_SEARCH).selectedIndex())
                .isEqualTo(3);
    }

    @Test
    void routesReviewAndReportQueuesAsCaseSearches() {
        AgentSkillRoute pendingReview = router.route("今天有哪些待审核结果？", null);
        assertThat(pendingReview.skillCode()).isEqualTo(AgentSkillCode.ASSIGNED_CASE_SEARCH);
        assertThat(pendingReview.arguments())
                .containsEntry("clinicalState", "PENDING_REVIEW")
                .containsEntry("dateWindow", "TODAY");

        AgentSkillRoute pendingReport = router.route("哪些结果已经审核但还没有签发？", null);
        assertThat(pendingReport.skillCode()).isEqualTo(AgentSkillCode.ASSIGNED_CASE_SEARCH);
        assertThat(pendingReport.arguments()).containsEntry("clinicalState", "PENDING_REPORT");
    }

    @Test
    void extractsDateEyeSideAndFailureFiltersFromNaturalLanguage() {
        AgentSkillRoute route = router.route("最近一个月右眼分割失败的病例", null);

        assertThat(route.skillCode()).isEqualTo(AgentSkillCode.ASSIGNED_CASE_SEARCH);
        assertThat(route.arguments())
                .containsEntry("segmentationState", "FAILED")
                .containsEntry("dateWindow", "LAST_30_DAYS")
                .containsEntry("eyeSide", "RIGHT");

        assertThat(router.route("最近有哪些任务失败？", null).skillCode())
                .isEqualTo(AgentSkillCode.ASSIGNED_CASE_SEARCH);
    }
}
