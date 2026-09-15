package com.example.retinavision.controller;

import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.service.AiReportDraftService;
import com.example.retinavision.service.AnalysisReportService;
import com.example.retinavision.service.ClinicalAccessService;
import com.example.retinavision.service.ResultHumanWorkflowService;
import com.example.retinavision.service.ResultReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultClinicalWorkflowControllerTest {

    private final ResultHumanWorkflowService humanService = mock(ResultHumanWorkflowService.class);
    private final ResultReviewService reviewService = mock(ResultReviewService.class);
    private final AnalysisReportService reportService = mock(AnalysisReportService.class);
    private final AiReportDraftService aiReportDraftService = mock(AiReportDraftService.class);
    private final ClinicalAccessService accessService = mock(ClinicalAccessService.class);
    private final ResultClinicalWorkflowController controller = new ResultClinicalWorkflowController(
            humanService,
            reviewService,
            reportService,
            aiReportDraftService,
            accessService
    );

    @Test
    void onlyDoctorCanGenerateAiReportDraft() {
        BaseException exception = catchThrowableOfType(
                () -> controller.generateDraft(1L, auth(new CurrentUserVO(7, "u", "User", UserRole.RESEARCHER))),
                BaseException.class);

        assertThat(exception.getCode()).isEqualTo(40300);
        verify(accessService).assertCanAccessResult(new CurrentUserVO(7, "u", "User", UserRole.RESEARCHER), 1L);
    }

    @Test
    void doctorCanGenerateAiReportDraft() {
        AnalysisReportEntity report = new AnalysisReportEntity();
        report.setVersion(1);
        report.setStatus(ReportStatus.DRAFT);
        report.setDraftJson("{\"findings\":\"ai\"}");
        when(aiReportDraftService.generateDraft(1L, 8)).thenReturn(report);

        var response = controller.generateDraft(1L, auth(new CurrentUserVO(8, "d", "Doctor", UserRole.DOCTOR)));

        assertThat(response.getData().draftJson()).contains("ai");
        verify(accessService).assertCanAccessResult(new CurrentUserVO(8, "d", "Doctor", UserRole.DOCTOR), 1L);
    }

    private TestingAuthenticationToken auth(CurrentUserVO user) {
        return new TestingAuthenticationToken(user, null);
    }
}
