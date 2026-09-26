package com.example.retinavision.service.impl;

import com.example.retinavision.config.DoctorReviewReminderProperties;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mapper.AnalysisReportMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.DoctorReviewReminderVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.enumeration.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoctorReviewReminderServiceImplTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final AnalysisResultMapper resultMapper = mock(AnalysisResultMapper.class);
    private final AnalysisReviewMapper reviewMapper = mock(AnalysisReviewMapper.class);
    private final AnalysisReportMapper reportMapper = mock(AnalysisReportMapper.class);
    private final CaseMapper caseMapper = mock(CaseMapper.class);
    private final ImageMapper imageMapper = mock(ImageMapper.class);
    private final DoctorReviewReminderProperties properties = new DoctorReviewReminderProperties();
    private final DoctorReviewReminderServiceImpl service = new DoctorReviewReminderServiceImpl(
            taskMapper,
            resultMapper,
            reviewMapper,
            reportMapper,
            caseMapper,
            imageMapper,
            properties
    );

    @Test
    void successfulVesselTaskWithoutReviewIsPendingAndOverdue() {
        properties.setOverdueThresholdMinutes(120);
        TaskEntity task = task(1L, LocalDateTime.now().minusHours(3));
        AnalysisResultEntity result = result(11L, task.getId());
        when(taskMapper.selectCompletedVesselTasksForDoctor(20)).thenReturn(List.of(task));
        when(resultMapper.selectOne(any())).thenReturn(result);
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(caseMapper.selectById(2L)).thenReturn(caseEntity("CASE-001"));
        when(imageMapper.selectById(3L)).thenReturn(image("fundus.png"));

        DoctorReviewReminderVO summary = service.getReminderSummary(doctor());

        assertThat(summary.getPendingReviewCount()).isEqualTo(1);
        assertThat(summary.getOverdueReviewCount()).isEqualTo(1);
        assertThat(summary.getPendingReportCount()).isZero();
        assertThat(summary.getLatestOverdueItems()).hasSize(1);
        assertThat(summary.getLatestOverdueItems().get(0).getReason()).isEqualTo("待医生审核");
        assertThat(summary.getLatestOverdueItems().get(0).getCaseNo()).isEqualTo("CASE-001");
    }

    @Test
    void pendingAndChangesRequestedReviewAreStillPendingReview() {
        TaskEntity first = task(1L, LocalDateTime.now().minusMinutes(10));
        TaskEntity second = task(2L, LocalDateTime.now().minusMinutes(10));
        when(taskMapper.selectCompletedVesselTasksForDoctor(20)).thenReturn(List.of(first, second));
        when(resultMapper.selectOne(any()))
                .thenReturn(result(11L, first.getId()))
                .thenReturn(result(12L, second.getId()));
        when(reviewMapper.selectOne(any()))
                .thenReturn(review(ReviewStatus.PENDING))
                .thenReturn(review(ReviewStatus.CHANGES_REQUESTED));

        DoctorReviewReminderVO summary = service.getReminderSummary(doctor());

        assertThat(summary.getPendingReviewCount()).isEqualTo(2);
        assertThat(summary.getOverdueReviewCount()).isZero();
    }

    @Test
    void approvedReviewWithoutSignedReportIsPendingReport() {
        TaskEntity task = task(1L, LocalDateTime.now().minusHours(3));
        when(taskMapper.selectCompletedVesselTasksForDoctor(20)).thenReturn(List.of(task));
        when(resultMapper.selectOne(any())).thenReturn(result(11L, task.getId()));
        when(reviewMapper.selectOne(any())).thenReturn(review(ReviewStatus.APPROVED));
        when(reportMapper.selectCount(any())).thenReturn(0L);

        DoctorReviewReminderVO summary = service.getReminderSummary(doctor());

        assertThat(summary.getPendingReviewCount()).isZero();
        assertThat(summary.getPendingReportCount()).isEqualTo(1);
        assertThat(summary.getOverdueReportCount()).isEqualTo(1);
        assertThat(summary.getLatestOverdueItems().get(0).getReason()).isEqualTo("待签发报告");
    }

    @Test
    void signedReportAndRejectedReviewAreNotPending() {
        TaskEntity signed = task(1L, LocalDateTime.now().minusHours(3));
        TaskEntity rejected = task(2L, LocalDateTime.now().minusHours(3));
        when(taskMapper.selectCompletedVesselTasksForDoctor(20)).thenReturn(List.of(signed, rejected));
        when(resultMapper.selectOne(any()))
                .thenReturn(result(11L, signed.getId()))
                .thenReturn(result(12L, rejected.getId()));
        when(reviewMapper.selectOne(any()))
                .thenReturn(review(ReviewStatus.APPROVED))
                .thenReturn(review(ReviewStatus.REJECTED));
        when(reportMapper.selectCount(any())).thenReturn(1L);

        DoctorReviewReminderVO summary = service.getReminderSummary(doctor());

        assertThat(summary.getPendingReviewCount()).isZero();
        assertThat(summary.getPendingReportCount()).isZero();
        assertThat(summary.getLatestOverdueItems()).isEmpty();
    }

    private TaskEntity task(Long id, LocalDateTime finishedAt) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setCaseId(2L);
        task.setImageFileId(3L);
        task.setStatus(TaskStatus.SUCCESS);
        task.setTaskType(TaskType.VESSEL_SEGMENTATION);
        task.setFinishedAt(finishedAt);
        return task;
    }

    private AnalysisResultEntity result(Long id, Long taskId) {
        AnalysisResultEntity result = new AnalysisResultEntity();
        result.setId(id);
        result.setTaskId(taskId);
        return result;
    }

    private AnalysisReviewEntity review(ReviewStatus status) {
        AnalysisReviewEntity review = new AnalysisReviewEntity();
        review.setStatus(status);
        return review;
    }

    private CaseEntity caseEntity(String caseNo) {
        CaseEntity medicalCase = new CaseEntity();
        medicalCase.setCaseNo(caseNo);
        return medicalCase;
    }

    private ImageFileEntity image(String filename) {
        ImageFileEntity image = new ImageFileEntity();
        image.setOriginalFilename(filename);
        return image;
    }

    private CurrentUserVO doctor() {
        return new CurrentUserVO(20, "doctor", "Doctor", UserRole.DOCTOR);
    }
}
