package com.example.retinavision.agent;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PatientAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.PatientAgentProgressVO;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;
import com.example.retinavision.pojo.VO.PatientAgentSignedReportVO;
import com.example.retinavision.service.PatientAgentQueryService;
import com.example.retinavision.service.PatientReportExplanationService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientAgentSecurityContractTest {
    private static final Set<String> ALLOWED_ACTION_PATHS = Set.of(
            "/cases/11/progress",
            "/cases/11/progress?section=reports",
            "/cases/11/images");

    @Test
    void patientProjectionsExcludeTechnicalAndUnsignedFields() {
        assertFields(PatientAgentCaseSummaryVO.class,
                "caseId", "caseNo", "eyeSide", "workflowStatus", "qualityStatus",
                "qualityMessage", "signedReportAvailable", "updatedAt");
        assertFields(PatientAgentProgressVO.class,
                "caseId", "caseNo", "currentStage", "stages", "qualityStatus",
                "qualityMessage", "nextHandler", "message", "updatedAt", "estimatedCompletionAt");
        assertFields(PatientAgentSignedReportVO.class,
                "caseId", "caseNo", "resultId", "version", "signedAt", "signerName", "conclusion");
        assertFields(PatientAgentReportDetailVO.class,
                "caseId", "caseNo", "resultId", "version", "signedAt", "signerName",
                "findings", "conclusion", "recommendation");
    }

    @Test
    void everyPatientQueryIsAccountScopedAndSignedReportsStaySignedOnly() throws IOException {
        String mapper = resource("/mapper/PatientAgentQueryMapper.xml");

        assertThat(mapper)
                .contains("JOIN patient_profile p ON p.id = c.patient_id")
                .contains("p.account_user_id = #{userId}")
                .contains("report.status = 'SIGNED'")
                .doesNotContain("SELECT *");
    }

    @Test
    void lowConfidenceWriteIntentCannotExecutePatientQueries() {
        PatientAgentQueryService queries = mock(PatientAgentQueryService.class);
        PatientAgentQueryContextService contexts = mock(PatientAgentQueryContextService.class);
        when(contexts.load(8L)).thenReturn(java.util.Optional.empty());
        AgentSkillRouter router = (question, current) ->
                new AgentSkillRoute(AgentSkillCode.MY_CASE_LIST, 0.20, Map.of());
        AgentSkillVersionBindingService versions = mock(AgentSkillVersionBindingService.class);
        PatientAgentSkillOrchestrator orchestrator = new PatientAgentSkillOrchestrator(
                router, queries, contexts, mock(PatientReportExplanationService.class), versions);

        assertThatThrownBy(() -> orchestrator.handle(8L, "帮我删除检查并重新创建", patient()))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("请说明");
        verify(queries, never()).listMyCases(any(), any(Integer.class), any(Integer.class), any());
    }

    @Test
    void javaAndBrowserKeepPatientNavigationOnTheThreeApprovedRoutes() throws IOException {
        String orchestrator = Files.readString(Path.of(
                "src/main/java/com/example/retinavision/agent/PatientAgentSkillOrchestrator.java"));
        String frontend = Files.readString(Path.of("frontend/src/views/agent/ClinicalAgent.vue"));

        assertThat(orchestrator).contains(
                "VIEW_CASE_PROGRESS", "VIEW_SIGNED_REPORT", "GO_TO_IMAGE_UPLOAD",
                "?section=reports");
        assertThat(frontend).contains("progress(\\?section=reports)?|images")
                .doesNotContain("router.push(action.targetPath)");
        assertThat(ALLOWED_ACTION_PATHS).allMatch(path -> path.startsWith("/cases/11/"));
    }

    @Test
    void patientSkillsAreReadOnlyAndCannotAcquireDoctorOrAdminCapabilities() {
        AgentSkillRegistry registry = new AgentSkillRegistry();

        assertThat(List.of(AgentSkillCode.MY_CASE_LIST, AgentSkillCode.MY_CASE_PROGRESS,
                AgentSkillCode.MY_SIGNED_REPORT, AgentSkillCode.PATIENT_KNOWLEDGE_QA))
                .allSatisfy(code -> {
                    assertThat(registry.isAvailable(code, UserRole.USER)).isTrue();
                    assertThat(registry.isAvailable(code, UserRole.DOCTOR)).isFalse();
                    assertThat(registry.isAvailable(code, UserRole.ADMIN)).isFalse();
                    assertThat(registry.argumentSchema(code).keySet())
                            .doesNotContain("delete", "create", "update", "sign", "retry", "cancel");
                });
    }

    private void assertFields(Class<?> type, String... expected) {
        Set<String> actual = Arrays.stream(type.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .filter(name -> !name.startsWith("$"))
                .collect(Collectors.toSet());
        assertThat(actual).containsExactlyInAnyOrder(expected);
        assertThat(actual).doesNotContain("qualityScore", "threshold", "maskUrl", "imagePath",
                "modelName", "modelVersion", "taskLogs", "draft", "diagnosisNote");
    }

    private CurrentUserVO patient() {
        return new CurrentUserVO(17, "patient", "患者", UserRole.USER);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
