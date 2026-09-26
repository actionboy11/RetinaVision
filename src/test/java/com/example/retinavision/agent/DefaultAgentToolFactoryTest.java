package com.example.retinavision.agent;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.mapper.AgentToolCallLogMapper;
import com.example.retinavision.pojo.Entity.AgentToolCallLogEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PatientSignedReportExplanationVO;
import com.example.retinavision.pojo.VO.PatientSignedReportVO;
import com.example.retinavision.rag.KnowledgeDocumentRetriever;
import com.example.retinavision.rag.KnowledgeAudiencePolicy;
import com.example.retinavision.service.CaseAnalysisTimelineService;
import com.example.retinavision.service.AgentClinicalReferenceService;
import com.example.retinavision.service.ClinicalAccessService;
import com.example.retinavision.service.QualityControlService;
import com.example.retinavision.service.TaskService;
import com.example.retinavision.service.CaseService;
import com.example.retinavision.service.PatientCaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAgentToolFactoryTest {
    private final KnowledgeDocumentRetriever retriever = mock(KnowledgeDocumentRetriever.class);
    private final KnowledgeAudiencePolicy audiencePolicy = mock(KnowledgeAudiencePolicy.class);
    private final ClinicalAccessService access = mock(ClinicalAccessService.class);
    private final AgentClinicalReferenceService references = mock(AgentClinicalReferenceService.class);
    private final TaskService tasks = mock(TaskService.class);
    private final CaseAnalysisTimelineService timelines = mock(CaseAnalysisTimelineService.class);
    private final QualityControlService quality = mock(QualityControlService.class);
    private final AgentToolCallLogMapper logs = mock(AgentToolCallLogMapper.class);
    private final CaseService cases = mock(CaseService.class);
    private final PatientCaseService patientCases = mock(PatientCaseService.class);

    @Test
    void adminDoesNotReceiveClinicalRecordTools() {
        AgentToolBundle bundle = factory().create(1L, user(UserRole.ADMIN), "trace");

        assertThat(names(bundle)).containsExactlyInAnyOrder(
                "searchMedicalKnowledge", "getQualityControlOverview", "listQualityRiskAlerts");
    }

    @Test
    void doctorReceivesClinicalReadToolsAndKnowledgeSearchIsAudited() {
        when(retriever.retrieve(any(Query.class))).thenReturn(List.of(Document.builder()
                .id("p1").text("血管分割用于辅助量化血管结构").score(0.91)
                .metadata("documentId", 2L).metadata("chunkId", 3L)
                .metadata("documentTitle", "分割说明").metadata("source", "院内规范")
                .build()));
        when(audiencePolicy.filter(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        AgentToolBundle bundle = factory().create(1L, user(UserRole.DOCTOR), "trace");

        assertThat(names(bundle)).contains("getAssignedCaseSummary", "getAnalysisTask",
                        "getCaseAnalysisTimeline", "compareRecentAnalysisResults", "compareAnalysisResults")
                .doesNotContain("getQualityControlOverview", "listQualityRiskAlerts");
        ToolCallback search = Arrays.stream(bundle.callbacks())
                .filter(tool -> tool.getToolDefinition().name().equals("searchMedicalKnowledge"))
                .findFirst().orElseThrow();
        assertThat(search.call("{\"question\":\"血管分割有什么作用\"}"))
                .contains("血管分割用于辅助量化血管结构");
        assertThat(bundle.citations()).hasSize(1);
        assertThat(bundle.summaries()).hasSize(1);
        verify(logs).insert(any(AgentToolCallLogEntity.class));
    }

    @Test
    void patientReceivesOnlyOwnCaseAndKnowledgeTools() {
        AgentToolBundle bundle = factory().create(1L, user(UserRole.USER), "trace");

        assertThat(names(bundle)).containsExactlyInAnyOrder(
                "searchMedicalKnowledge", "listMyCases", "getMyCaseProgress", "explainMySignedReport")
                .doesNotContain("getAnalysisTask", "getQualityControlOverview");
    }

    @Test
    void patientReportToolAutomaticallyUsesLatestSignedReport() {
        CurrentUserVO patient = user(UserRole.USER);
        when(patientCases.signedReports(12L, patient)).thenReturn(List.of(
                new PatientSignedReportVO(99L, 3, ReportStatus.SIGNED,
                        LocalDateTime.now(), "医生", "sha")));
        when(patientCases.signedReportExplanation(12L, 99L, 3, patient)).thenReturn(
                new PatientSignedReportExplanationVO(99L, 3, "所见", "结论", "建议"));
        AgentToolBundle bundle = factory().create(1L, patient, "trace");
        ToolCallback explain = Arrays.stream(bundle.callbacks())
                .filter(tool -> tool.getToolDefinition().name().equals("explainMySignedReport"))
                .findFirst().orElseThrow();

        assertThat(explain.call("{\"caseId\":12}")).contains("结论");
        verify(patientCases).signedReportExplanation(12L, 99L, 3, patient);
    }

    @Test
    void skillScopeRegistersOnlyAllowlistedTools() {
        AgentToolBundle bundle = factory().create(1L, user(UserRole.DOCTOR), "trace",
                Set.of("searchMedicalKnowledge"));

        assertThat(names(bundle)).containsExactly("searchMedicalKnowledge");
    }

    private DefaultAgentToolFactory factory() {
        return new DefaultAgentToolFactory(retriever, audiencePolicy, access, references, tasks, timelines, quality,
                cases, patientCases, logs, new ObjectMapper());
    }

    private CurrentUserVO user(UserRole role) {
        return new CurrentUserVO(7, "tester", "测试", role);
    }

    private List<String> names(AgentToolBundle bundle) {
        return Arrays.stream(bundle.callbacks()).map(tool -> tool.getToolDefinition().name()).toList();
    }
}
