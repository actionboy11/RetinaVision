package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.mapper.AnalysisReportMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.service.ClinicalAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientCaseServiceImplTest {
    @Mock private CaseMapper cases;
    @Mock private ImageMapper images;
    @Mock private TaskMapper tasks;
    @Mock private AnalysisResultMapper results;
    @Mock private AnalysisReportMapper reports;
    @Mock private AnalysisReviewMapper reviews;
    @Mock private ClinicalAccessService access;

    private PatientCaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PatientCaseServiceImpl(cases, images, tasks, results, reports, reviews, access);
    }

    @Test
    void progressContainsOnlyPatientSafeStatusSummary() {
        when(cases.selectById(10L)).thenReturn(CaseEntity.builder().id(10).caseNo("C-10")
                .patientCode("PT-AAAA-BBBB").workflowStatus(CaseWorkflowStatus.IN_REVIEW)
                .updatedAt(LocalDateTime.of(2026, 9, 25, 9, 0)).build());
        when(images.selectList(any())).thenReturn(List.of(ImageFileEntity.builder()
                .qualityStatus(ImageQualityStatus.WARNING).build()));
        when(tasks.selectList(any())).thenReturn(List.of(TaskEntity.builder()
                .taskType(TaskType.VESSEL_SEGMENTATION).status(TaskStatus.RUNNING).build()));

        var progress = service.progress(10L, patient());

        assertThat(progress.patientNo()).isEqualTo("PT-AAAA-BBBB");
        assertThat(progress.qualityStatus()).isEqualTo(ImageQualityStatus.WARNING);
        assertThat(progress.analysisStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void signedReportsExcludeDrafts() {
        when(cases.selectById(10L)).thenReturn(CaseEntity.builder().id(10).build());
        when(tasks.selectList(any())).thenReturn(List.of(TaskEntity.builder().id(20L).build()));
        when(results.selectList(any())).thenReturn(List.of(result(30L, 20L)));
        when(reports.selectList(any())).thenReturn(List.of(
                report(30L, 1, ReportStatus.DRAFT),
                report(30L, 2, ReportStatus.SIGNED)));

        var visible = service.signedReports(10L, patient());

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).version()).isEqualTo(2);
    }

    @Test
    void signedReportExplanationUsesDoctorOpinion() {
        when(cases.selectById(10L)).thenReturn(CaseEntity.builder().id(10).build());
        when(tasks.selectList(any())).thenReturn(List.of(TaskEntity.builder().id(20L).build()));
        when(results.selectList(any())).thenReturn(List.of(result(30L, 20L)));
        when(reports.selectList(any())).thenReturn(List.of(report(30L, 2, ReportStatus.SIGNED)));
        AnalysisReviewEntity review = new AnalysisReviewEntity();
        review.setFindings("医生所见");
        review.setConclusion("医生结论");
        review.setRecommendation("医生建议");
        when(reviews.selectOne(any())).thenReturn(review);

        var explanation = service.signedReportExplanation(10L, 30L, 2, patient());

        assertThat(explanation.conclusion()).isEqualTo("医生结论");
        assertThat(explanation.recommendation()).isEqualTo("医生建议");
    }

    private CurrentUserVO patient() {
        return new CurrentUserVO(7, "patient", "Patient", UserRole.USER);
    }

    private AnalysisResultEntity result(long id, long taskId) {
        AnalysisResultEntity result = new AnalysisResultEntity();
        result.setId(id);
        result.setTaskId(taskId);
        return result;
    }

    private AnalysisReportEntity report(long resultId, int version, ReportStatus status) {
        AnalysisReportEntity report = new AnalysisReportEntity();
        report.setResultId(resultId);
        report.setVersion(version);
        report.setStatus(status);
        return report;
    }
}
