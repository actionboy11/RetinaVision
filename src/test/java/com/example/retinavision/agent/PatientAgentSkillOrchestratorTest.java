package com.example.retinavision.agent;

import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PatientAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.PatientAgentProgressVO;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;
import com.example.retinavision.pojo.VO.PatientAgentSignedReportVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.PatientAgentQueryService;
import com.example.retinavision.service.PatientReportExplanationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientAgentSkillOrchestratorTest {

    @Test
    void listsLatestCasesWithPageSizeTenAndAllowlistedActions() {
        PatientAgentQueryService queries = mock(PatientAgentQueryService.class);
        PatientAgentQueryContextService contexts = mock(PatientAgentQueryContextService.class);
        when(contexts.load(5L)).thenReturn(Optional.empty());
        var item = caseItem(11L, "C-11");
        when(queries.listMyCases(any(), eq(1), eq(10), any()))
                .thenReturn(new PageResult<>(List.of(item), 12, 1, 10));
        var orchestrator = orchestrator(route(AgentSkillCode.MY_CASE_LIST, Map.of()), queries, contexts);

        var result = orchestrator.handle(5L, "我有哪些检查", patient()).orElseThrow();

        assertThat(result.data().type()).isEqualTo("CASE_LIST");
        assertThat(result.pagination().hasNext()).isTrue();
        assertThat(result.actions()).allSatisfy(action -> {
            if (action.targetPath() != null) {
                assertThat(action.type()).isEqualTo("VIEW_CASE_PROGRESS");
                assertThat(action.targetPath()).isEqualTo("/cases/11/progress");
            }
        });
        verify(queries).listMyCases(any(), eq(1), eq(10), any());
        verify(contexts).save(eq(5L), any());
    }

    @Test
    void nextPreviousReuploadAndSelectionUseStoredReferences() {
        PatientAgentQueryService queries = mock(PatientAgentQueryService.class);
        PatientAgentQueryContextService contexts = mock(PatientAgentQueryContextService.class);
        var context = new PatientAgentQueryContextSnapshot(AgentSkillCode.MY_CASE_LIST,
                false, false, 1, 10, 20, null, null, null,
                List.of(new PatientAgentReference(11L, "C-11", null, null),
                        new PatientAgentReference(12L, "C-12", null, null)));
        when(contexts.load(5L)).thenReturn(Optional.of(context));
        when(queries.listMyCases(any(), eq(2), eq(10), any()))
                .thenReturn(new PageResult<>(List.of(caseItem(13L, "C-13")), 20, 2, 10));
        var orchestrator = orchestrator(mock(AgentSkillRouter.class), queries, contexts);

        assertThat(orchestrator.handle(5L, "下一页", patient()).orElseThrow().pagination().page()).isEqualTo(2);

        when(contexts.load(5L)).thenReturn(Optional.of(context));
        when(queries.getMyCaseProgress("12", patient())).thenReturn(progress(12L));
        var selected = orchestrator.handle(5L, "查看第二个", patient()).orElseThrow();
        assertThat(selected.data().type()).isEqualTo("CASE_PROGRESS");
        verify(queries).getMyCaseProgress("12", patient());
    }

    @Test
    void latestProgressUsesNewestCaseWithoutSendingDataToModel() {
        PatientAgentQueryService queries = mock(PatientAgentQueryService.class);
        when(queries.listMyCases(any(), eq(1), eq(1), any()))
                .thenReturn(new PageResult<>(List.of(caseItem(11L, "C-11")), 1, 1, 1));
        when(queries.getMyCaseProgress("11", patient())).thenReturn(progress(11L));
        var orchestrator = orchestrator(route(AgentSkillCode.MY_CASE_PROGRESS, Map.of()), queries,
                mock(PatientAgentQueryContextService.class));

        var result = orchestrator.handle(5L, "我最近一次检查到哪一步了", patient()).orElseThrow();

        assertThat(result.answer()).contains("医生处理");
        assertThat(result.actions()).anySatisfy(action ->
                assertThat(action.targetPath()).isEqualTo("/cases/11/progress"));
    }

    @Test
    void emptyLatestCaseReturnsStructuredEmptyResponse() {
        PatientAgentQueryService queries = mock(PatientAgentQueryService.class);
        when(queries.listMyCases(any(), eq(1), eq(1), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 1));

        var result = orchestrator(route(AgentSkillCode.MY_CASE_PROGRESS, Map.of()), queries,
                mock(PatientAgentQueryContextService.class))
                .handle(5L, "我最近一次检查到哪一步了", patient()).orElseThrow();

        assertThat(result.answer()).isEqualTo("当前还没有检查记录。 ".trim());
        assertThat(result.actions()).isEmpty();
    }

    @Test
    void reportListViewAndExplanationRemainNativeAndPatientScoped() {
        PatientAgentQueryService queries = mock(PatientAgentQueryService.class);
        PatientReportExplanationService explanations = mock(PatientReportExplanationService.class);
        PatientAgentSignedReportVO card = new PatientAgentSignedReportVO();
        card.setCaseId(11L); card.setCaseNo("C-11"); card.setResultId(91L); card.setVersion(2);
        when(queries.listMySignedReports("", 1, 10, patient()))
                .thenReturn(new PageResult<>(List.of(card), 1, 1, 10));
        var context = new PatientAgentQueryContextSnapshot(AgentSkillCode.MY_SIGNED_REPORT,
                false, true, 1, 10, 1, 11L, 91L, 2,
                List.of(new PatientAgentReference(11L, "C-11", 91L, 2)));
        PatientAgentQueryContextService contexts = mock(PatientAgentQueryContextService.class);
        when(contexts.load(5L)).thenReturn(Optional.empty());
        var orchestrator = orchestrator(route(AgentSkillCode.MY_SIGNED_REPORT, Map.of("mode", "LIST")),
                queries, contexts, explanations);

        var list = orchestrator.handle(5L, "查看我的正式报告", patient()).orElseThrow();
        assertThat(list.data().type()).isEqualTo("SIGNED_REPORT");
        assertThat(list.actions()).anySatisfy(action -> {
            assertThat(action.type()).isEqualTo("VIEW_SIGNED_REPORT");
            assertThat(action.targetPath()).isEqualTo("/cases/11/progress?section=reports");
        });

        when(contexts.load(5L)).thenReturn(Optional.of(context));
        PatientAgentReportDetailVO detail = new PatientAgentReportDetailVO();
        detail.setCaseId(11L); detail.setCaseNo("C-11"); detail.setResultId(91L); detail.setVersion(2);
        when(queries.getMySignedReport("11", 91L, 2, patient())).thenReturn(detail);
        when(explanations.explain(detail)).thenReturn(PatientReportExplanationResult.available("通俗解释"));
        var explained = orchestrator.handle(5L, "解释这份报告", patient()).orElseThrow();
        assertThat(explained.data().payload()).containsEntry("explanation", "通俗解释");
    }

    @Test
    void expiredOutOfRangeAndWrongRoleFailWithoutCrossingBoundary() {
        PatientAgentQueryService queries = mock(PatientAgentQueryService.class);
        PatientAgentQueryContextService contexts = mock(PatientAgentQueryContextService.class);
        when(contexts.load(5L)).thenReturn(Optional.empty());
        var orchestrator = orchestrator(mock(AgentSkillRouter.class), queries, contexts);

        assertThatThrownBy(() -> orchestrator.handle(5L, "查看第二个", patient()))
                .isInstanceOf(BaseException.class).hasMessageContaining("重新查询");
        assertThat(orchestrator.handle(5L, "我的检查", doctor())).isEmpty();
        verify(queries, never()).getMyCaseProgress(any(), eq(doctor()));
    }

    @Test
    void patientKnowledgeDelegatesToControlledToolCallingPath() {
        var orchestrator = orchestrator(route(AgentSkillCode.PATIENT_KNOWLEDGE_QA, Map.of()),
                mock(PatientAgentQueryService.class), mock(PatientAgentQueryContextService.class));

        assertThat(orchestrator.handle(5L, "血管分割是什么意思", patient())).isEmpty();
    }

    private PatientAgentSkillOrchestrator orchestrator(AgentSkillRoute route,
                                                         PatientAgentQueryService queries,
                                                         PatientAgentQueryContextService contexts) {
        return orchestrator(route, queries, contexts, mock(PatientReportExplanationService.class));
    }

    private PatientAgentSkillOrchestrator orchestrator(AgentSkillRoute route,
                                                         PatientAgentQueryService queries,
                                                         PatientAgentQueryContextService contexts,
                                                         PatientReportExplanationService explanations) {
        return orchestrator((question, current) -> route, queries, contexts, explanations);
    }

    private PatientAgentSkillOrchestrator orchestrator(AgentSkillRouter router,
                                                         PatientAgentQueryService queries,
                                                         PatientAgentQueryContextService contexts) {
        return orchestrator(router, queries, contexts, mock(PatientReportExplanationService.class));
    }

    private PatientAgentSkillOrchestrator orchestrator(AgentSkillRouter router,
                                                         PatientAgentQueryService queries,
                                                         PatientAgentQueryContextService contexts,
                                                         PatientReportExplanationService explanations) {
        AgentSkillVersionBindingService versions = mock(AgentSkillVersionBindingService.class);
        when(versions.resolve(any(), any())).thenReturn(new AgentSkillRuntimeVersion(1L, 1));
        return new PatientAgentSkillOrchestrator(router, queries, contexts, explanations, versions);
    }

    private AgentSkillRoute route(AgentSkillCode code, Map<String, String> arguments) {
        return new AgentSkillRoute(code, 0.98, arguments);
    }

    private PatientAgentCaseSummaryVO caseItem(long id, String no) {
        PatientAgentCaseSummaryVO item = new PatientAgentCaseSummaryVO();
        item.setCaseId(id); item.setCaseNo(no); item.setEyeSide("BOTH");
        item.setWorkflowStatus(CaseWorkflowStatus.SUBMITTED);
        item.setUpdatedAt(LocalDateTime.of(2026, 10, 7, 9, 0));
        return item;
    }

    private PatientAgentProgressVO progress(long id) {
        PatientAgentProgressVO progress = new PatientAgentProgressVO();
        progress.setCaseId(id); progress.setCaseNo("C-" + id);
        progress.setCurrentStage("DOCTOR_PROCESSING");
        progress.setNextHandler("负责医生");
        progress.setStages(List.of());
        return progress;
    }

    private CurrentUserVO patient() {
        return new CurrentUserVO(17, "patient", "患者", UserRole.USER);
    }

    private CurrentUserVO doctor() {
        return new CurrentUserVO(7, "doctor", "医生", UserRole.DOCTOR);
    }
}
