package com.example.retinavision.service.impl;

import com.example.retinavision.agent.PatientCaseSearchCriteria;
import com.example.retinavision.agent.PatientQualityPresenter;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.PatientAgentQueryMapper;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;
import com.example.retinavision.pojo.VO.PatientAgentSignedReportVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientAgentQueryServiceImplTest {

    @Test
    void listScopesToCurrentAccountAndCapsPageSize() {
        PatientAgentQueryMapper mapper = mock(PatientAgentQueryMapper.class);
        PatientAgentQueryServiceImpl service = service(mapper);
        PatientCaseSearchCriteria criteria = new PatientCaseSearchCriteria(true, false);
        var projection = new PatientAgentQueryMapper.CaseProjection();
        projection.setCaseId(9L);
        projection.setCaseNo("C-1");
        projection.setQualityStatus("FAIL");
        projection.setQualityReason("BLUR_DETECTED");
        when(mapper.countMyCases(17, criteria)).thenReturn(1L);
        when(mapper.selectMyCases(17, criteria, 0, 10))
                .thenReturn(List.of(projection));

        var result = service.listMyCases(criteria, 0, 100, patient(17));

        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getQualityStatus()).hasToString("REUPLOAD_RECOMMENDED");
            assertThat(item.getQualityMessage()).doesNotContain("BLUR_DETECTED");
        });
        verify(mapper).countMyCases(17, criteria);
        verify(mapper).selectMyCases(17, criteria, 0, 10);
    }

    @Test
    void listReturnsEmptyRecordsInsteadOfNull() {
        PatientAgentQueryMapper mapper = mock(PatientAgentQueryMapper.class);
        PatientAgentQueryServiceImpl service = service(mapper);
        PatientCaseSearchCriteria criteria = new PatientCaseSearchCriteria(false, false);
        when(mapper.countMyCases(17, criteria)).thenReturn(0L);
        when(mapper.selectMyCases(17, criteria, 0, 10)).thenReturn(null);

        var result = service.listMyCases(criteria, 1, 10, patient(17));

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void progressResolvesNumericOrBusinessReferenceWithinPatientScope() {
        PatientAgentQueryMapper mapper = mock(PatientAgentQueryMapper.class);
        PatientAgentQueryServiceImpl service = service(mapper);
        var projection = new PatientAgentQueryMapper.ProgressProjection();
        projection.setCaseId(42L);
        projection.setCaseNo("C-20261007-001");
        projection.setImageCount(1);
        projection.setQualityStatus("PASS");
        projection.setAnalysisStatus("RUNNING");
        projection.setSignedReportCount(0);
        projection.setUpdatedAt(LocalDateTime.of(2026, 10, 7, 9, 0));
        when(mapper.selectMyCaseProgress(17, "42", 42L)).thenReturn(projection);

        var result = service.getMyCaseProgress("42", patient(17));

        verify(mapper).selectMyCaseProgress(17, "42", 42L);
        assertThat(result.getStages()).hasSize(4);
        assertThat(result.getCurrentStage()).isEqualTo("DOCTOR_PROCESSING");
        assertThat(result.getNextHandler()).isEqualTo("负责医生");
        assertThat(result.getEstimatedCompletionAt()).isNull();
    }

    @Test
    void hiddenOrUnknownCaseUsesSameNotFoundResponse() {
        PatientAgentQueryMapper mapper = mock(PatientAgentQueryMapper.class);
        PatientAgentQueryServiceImpl service = service(mapper);
        when(mapper.selectMyCaseProgress(17, "C-OTHER", null)).thenReturn(null);

        assertThatThrownBy(() -> service.getMyCaseProgress("C-OTHER", patient(17)))
                .isInstanceOf(BaseException.class)
                .hasMessage("资源不存在");
    }

    @Test
    void signedReportsArePagedAndDetailedProjectionContainsOnlySignedDoctorFields() {
        PatientAgentQueryMapper mapper = mock(PatientAgentQueryMapper.class);
        PatientAgentQueryServiceImpl service = service(mapper);
        when(mapper.countMySignedReports(17, "C-1", null)).thenReturn(1L);
        when(mapper.selectMySignedReports(17, "C-1", null, 0, 10))
                .thenReturn(List.of(new PatientAgentSignedReportVO()));
        PatientAgentReportDetailVO detail = new PatientAgentReportDetailVO();
        detail.setCaseId(9L);
        detail.setCaseNo("C-1");
        detail.setResultId(91L);
        detail.setVersion(2);
        detail.setFindings("医生所见");
        detail.setConclusion("医生结论");
        detail.setRecommendation("复查建议");
        when(mapper.selectMySignedReport(17, "C-1", null, 91L, 2)).thenReturn(detail);

        var page = service.listMySignedReports("C-1", 1, 99, patient(17));
        var found = service.getMySignedReport("C-1", 91L, 2, patient(17));

        assertThat(page.getPageSize()).isEqualTo(10);
        assertThat(found).isSameAs(detail);
        verify(mapper).selectMySignedReport(17, "C-1", null, 91L, 2);
    }

    @Test
    void allQueriesRequirePatientRoleBeforeMapperAccess() {
        PatientAgentQueryMapper mapper = mock(PatientAgentQueryMapper.class);
        PatientAgentQueryServiceImpl service = service(mapper);
        CurrentUserVO doctor = new CurrentUserVO(7, "doctor", "医生", UserRole.DOCTOR);

        assertThatThrownBy(() -> service.listMyCases(null, 1, 10, doctor)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.getMyCaseProgress("1", doctor)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.listMySignedReports(null, 1, 10, doctor)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.getMySignedReport(null, 1L, null, doctor)).isInstanceOf(BaseException.class);
        verify(mapper, never()).countMyCases(7, new PatientCaseSearchCriteria(false, false));
    }

    private PatientAgentQueryServiceImpl service(PatientAgentQueryMapper mapper) {
        return new PatientAgentQueryServiceImpl(mapper, new PatientQualityPresenter());
    }

    private CurrentUserVO patient(int id) {
        return new CurrentUserVO(id, "patient", "患者", UserRole.USER);
    }
}
