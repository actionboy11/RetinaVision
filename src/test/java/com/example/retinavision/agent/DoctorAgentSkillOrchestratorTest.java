package com.example.retinavision.agent;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.DoctorWorkloadVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.AgentClinicalReferenceService;
import com.example.retinavision.service.CaseAnalysisTimelineService;
import com.example.retinavision.service.DoctorAgentQueryService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class DoctorAgentSkillOrchestratorTest {

    @Test
    void workloadQuestionReturnsTrustedStructuredMetrics() {
        DoctorAgentQueryService queries = mock(DoctorAgentQueryService.class);
        AgentQueryContextService contexts = mock(AgentQueryContextService.class);
        CurrentUserVO doctor = doctor();
        when(queries.getClinicalWorkload(doctor)).thenReturn(new DoctorWorkloadVO(8, 11, 3, 4, 2, 1));
        AgentSkillVersionBindingService versions = mock(AgentSkillVersionBindingService.class);
        when(versions.resolve(9L, AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW))
                .thenReturn(new AgentSkillRuntimeVersion(101L, 3));
        DoctorAgentSkillOrchestrator orchestrator = new DoctorAgentSkillOrchestrator(
                new DefaultAgentSkillRouter(), queries, contexts,
                mock(AgentClinicalReferenceService.class), mock(CaseAnalysisTimelineService.class), versions);

        Optional<DoctorAgentSkillResult> result = orchestrator.handle(9L, "我有多少名患者？", doctor);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().skillCode()).isEqualTo(AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW);
        assertThat(result.orElseThrow().data().type()).isEqualTo("METRICS");
        assertThat(result.orElseThrow().skillVersion()).isEqualTo(3);
        assertThat(result.orElseThrow().answer()).contains("8", "11");
    }

    @Test
    void caseSearchPassesNaturalLanguageFiltersToTrustedQuery() {
        DoctorAgentQueryService queries = mock(DoctorAgentQueryService.class);
        AgentQueryContextService contexts = mock(AgentQueryContextService.class);
        CurrentUserVO doctor = doctor();
        when(contexts.load(9L)).thenReturn(Optional.empty());
        when(queries.searchAssignedCases(any(), eq(1), eq(10), eq(doctor)))
                .thenReturn(new PageResult<DoctorAgentCaseSummaryVO>(List.of(), 0, 1, 10));
        AgentSkillVersionBindingService versions = mock(AgentSkillVersionBindingService.class);
        when(versions.resolve(9L, AgentSkillCode.ASSIGNED_CASE_SEARCH))
                .thenReturn(new AgentSkillRuntimeVersion(102L, 1));
        DoctorAgentSkillOrchestrator orchestrator = orchestrator(queries, contexts, versions);

        orchestrator.handle(9L, "今天有哪些右眼待审核结果？", doctor);

        ArgumentCaptor<DoctorCaseSearchCriteria> criteria = ArgumentCaptor.forClass(DoctorCaseSearchCriteria.class);
        verify(queries).searchAssignedCases(criteria.capture(), eq(1), eq(10), eq(doctor));
        assertThat(criteria.getValue().clinicalState()).isEqualTo(DoctorClinicalState.PENDING_REVIEW);
        assertThat(criteria.getValue().dateWindow()).isEqualTo(DoctorDateWindow.TODAY);
        assertThat(criteria.getValue().eyeSide()).isEqualTo(EyeSide.RIGHT);
    }

    @Test
    void nextPagePreservesEverySearchFilterFromContext() {
        DoctorAgentQueryService queries = mock(DoctorAgentQueryService.class);
        AgentQueryContextService contexts = mock(AgentQueryContextService.class);
        CurrentUserVO doctor = doctor();
        when(contexts.load(9L)).thenReturn(Optional.of(new AgentQueryContextSnapshot(
                AgentSkillCode.ASSIGNED_CASE_SEARCH, SegmentationState.FAILED,
                DoctorClinicalState.PENDING_REVIEW, DoctorDateWindow.LAST_30_DAYS, EyeSide.LEFT,
                1, 10, 20, null, null, List.of(1, 2))));
        DoctorAgentCaseSummaryVO caseSummary = new DoctorAgentCaseSummaryVO();
        caseSummary.setCaseId(3);
        when(queries.searchAssignedCases(any(), eq(2), eq(10), eq(doctor)))
                .thenReturn(new PageResult<DoctorAgentCaseSummaryVO>(List.of(caseSummary), 20, 2, 10));
        AgentSkillVersionBindingService versions = mock(AgentSkillVersionBindingService.class);
        when(versions.resolve(9L, AgentSkillCode.ASSIGNED_CASE_SEARCH))
                .thenReturn(new AgentSkillRuntimeVersion(102L, 1));
        DoctorAgentSkillOrchestrator orchestrator = orchestrator(queries, contexts, versions);

        orchestrator.handle(9L, "下一页", doctor);

        ArgumentCaptor<DoctorCaseSearchCriteria> criteria = ArgumentCaptor.forClass(DoctorCaseSearchCriteria.class);
        verify(queries).searchAssignedCases(criteria.capture(), eq(2), eq(10), eq(doctor));
        assertThat(criteria.getValue().segmentationState()).isEqualTo(SegmentationState.FAILED);
        assertThat(criteria.getValue().clinicalState()).isEqualTo(DoctorClinicalState.PENDING_REVIEW);
        assertThat(criteria.getValue().dateWindow()).isEqualTo(DoctorDateWindow.LAST_30_DAYS);
        assertThat(criteria.getValue().eyeSide()).isEqualTo(EyeSide.LEFT);
    }

    private DoctorAgentSkillOrchestrator orchestrator(DoctorAgentQueryService queries,
                                                        AgentQueryContextService contexts,
                                                        AgentSkillVersionBindingService versions) {
        return new DoctorAgentSkillOrchestrator(new DefaultAgentSkillRouter(), queries, contexts,
                mock(AgentClinicalReferenceService.class), mock(CaseAnalysisTimelineService.class), versions);
    }

    private CurrentUserVO doctor() {
        CurrentUserVO user = new CurrentUserVO();
        user.setId(27);
        user.setRoleCode(UserRole.DOCTOR);
        return user;
    }
}
