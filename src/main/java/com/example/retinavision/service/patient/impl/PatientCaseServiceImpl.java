package com.example.retinavision.service.patient.impl;

import com.example.retinavision.constant.shared.ErrorMessageSignal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.enumeration.imaging.ImageQualityStatus;
import com.example.retinavision.enumeration.reporting.ReportStatus;
import com.example.retinavision.enumeration.analysis.TaskStatus;
import com.example.retinavision.enumeration.analysis.TaskType;
import com.example.retinavision.enumeration.user.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.reporting.AnalysisReportMapper;
import com.example.retinavision.mapper.analysis.AnalysisResultMapper;
import com.example.retinavision.mapper.clinical.AnalysisReviewMapper;
import com.example.retinavision.mapper.cases.CaseMapper;
import com.example.retinavision.mapper.imaging.ImageMapper;
import com.example.retinavision.mapper.analysis.TaskMapper;
import com.example.retinavision.pojo.reporting.entity.AnalysisReportEntity;
import com.example.retinavision.pojo.analysis.entity.AnalysisResultEntity;
import com.example.retinavision.pojo.clinical.entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.cases.entity.CaseEntity;
import com.example.retinavision.pojo.imaging.entity.ImageFileEntity;
import com.example.retinavision.pojo.analysis.entity.TaskEntity;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
import com.example.retinavision.pojo.patient.vo.PatientCaseProgressVO;
import com.example.retinavision.pojo.cases.vo.CaseListItemVO;
import com.example.retinavision.pojo.reporting.vo.PatientSignedReportVO;
import com.example.retinavision.pojo.reporting.vo.PatientSignedReportExplanationVO;
import com.example.retinavision.service.cases.ClinicalAccessService;
import com.example.retinavision.service.patient.PatientCaseService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class PatientCaseServiceImpl implements PatientCaseService {
    private final CaseMapper cases;
    private final ImageMapper images;
    private final TaskMapper tasks;
    private final AnalysisResultMapper results;
    private final AnalysisReportMapper reports;
    private final AnalysisReviewMapper reviews;
    private final ClinicalAccessService access;

    public PatientCaseServiceImpl(CaseMapper cases, ImageMapper images, TaskMapper tasks,
                                  AnalysisResultMapper results, AnalysisReportMapper reports,
                                  AnalysisReviewMapper reviews,
                                  ClinicalAccessService access) {
        this.cases = cases;
        this.images = images;
        this.tasks = tasks;
        this.results = results;
        this.reports = reports;
        this.reviews = reviews;
        this.access = access;
    }

    @Override
    public PatientCaseProgressVO progress(Long caseId, CurrentUserVO patient) {
        requirePatient(patient);
        access.assertCanAccessCase(patient, caseId);
        CaseEntity medicalCase = requireCase(caseId);
        List<ImageFileEntity> caseImages = images.selectList(new LambdaQueryWrapper<ImageFileEntity>()
                .eq(ImageFileEntity::getCaseId, caseId).isNull(ImageFileEntity::getDeletedAt));
        List<TaskEntity> caseTasks = taskList(caseId);
        ImageQualityStatus quality = caseImages.stream().map(ImageFileEntity::getQualityStatus)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.comparingInt(this::qualitySeverity))
                .orElse(ImageQualityStatus.NOT_CHECKED);
        TaskStatus analysis = caseTasks.stream()
                .filter(task -> task.getTaskType() == TaskType.VESSEL_SEGMENTATION)
                .max(Comparator.comparing(TaskEntity::getSubmittedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(TaskEntity::getStatus).orElse(null);
        int signedCount = signedReportsInternal(caseTasks).size();
        CaseListItemVO caseView = cases.getCaseById(caseId.intValue());
        String patientNo = caseView == null || caseView.getPatientNo() == null
                ? medicalCase.getPatientCode() : caseView.getPatientNo();
        return new PatientCaseProgressVO(caseId, medicalCase.getCaseNo(), patientNo,
                medicalCase.getWorkflowStatus(), caseImages.size(), quality, analysis,
                signedCount, medicalCase.getUpdatedAt());
    }

    @Override
    public List<PatientSignedReportVO> signedReports(Long caseId, CurrentUserVO patient) {
        requirePatient(patient);
        access.assertCanAccessCase(patient, caseId);
        requireCase(caseId);
        return signedReportsInternal(taskList(caseId));
    }

    @Override
    public void assertSignedReportAccessible(Long caseId, Long resultId, Integer version, CurrentUserVO patient) {
        boolean allowed = signedReports(caseId, patient).stream()
                .anyMatch(report -> report.resultId().equals(resultId) && report.version().equals(version));
        if (!allowed) throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
    }

    @Override
    public PatientSignedReportExplanationVO signedReportExplanation(Long caseId, Long resultId,
                                                                    Integer version, CurrentUserVO patient) {
        assertSignedReportAccessible(caseId, resultId, version, patient);
        AnalysisReviewEntity review = reviews.selectOne(new LambdaQueryWrapper<AnalysisReviewEntity>()
                .eq(AnalysisReviewEntity::getResultId, resultId));
        if (review == null) throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
        return new PatientSignedReportExplanationVO(resultId, version, review.getFindings(),
                review.getConclusion(), review.getRecommendation());
    }

    private List<PatientSignedReportVO> signedReportsInternal(List<TaskEntity> caseTasks) {
        List<Long> taskIds = caseTasks.stream().map(TaskEntity::getId).filter(java.util.Objects::nonNull).toList();
        if (taskIds.isEmpty()) return List.of();
        List<AnalysisResultEntity> caseResults = results.selectList(new LambdaQueryWrapper<AnalysisResultEntity>()
                .in(AnalysisResultEntity::getTaskId, taskIds));
        Set<Long> resultIds = caseResults.stream().map(AnalysisResultEntity::getId).collect(java.util.stream.Collectors.toSet());
        if (resultIds.isEmpty()) return List.of();
        return reports.selectList(new LambdaQueryWrapper<AnalysisReportEntity>()
                        .in(AnalysisReportEntity::getResultId, resultIds)
                        .in(AnalysisReportEntity::getStatus, ReportStatus.SIGNED, ReportStatus.SUPERSEDED)
                        .orderByDesc(AnalysisReportEntity::getSignedAt))
                .stream().filter(report -> report.getStatus() == ReportStatus.SIGNED
                        || report.getStatus() == ReportStatus.SUPERSEDED)
                .map(report -> new PatientSignedReportVO(report.getResultId(), report.getVersion(),
                        report.getStatus(), report.getSignedAt(), report.getSignerNameSnapshot(),
                        report.getReportSha256()))
                .toList();
    }

    private List<TaskEntity> taskList(Long caseId) {
        return tasks.selectList(new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getCaseId, caseId));
    }

    private CaseEntity requireCase(Long caseId) {
        CaseEntity medicalCase = cases.selectById(caseId);
        if (medicalCase == null || medicalCase.getDeletedAt() != null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
        }
        return medicalCase;
    }

    private void requirePatient(CurrentUserVO user) {
        if (user == null || user.getRoleCode() != UserRole.USER) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "仅患者本人可以查看检查进度");
        }
    }

    private int qualitySeverity(ImageQualityStatus status) {
        return switch (status) {
            case ERROR -> 6;
            case FAIL -> 5;
            case WARNING -> 4;
            case CHECKING -> 3;
            case PASS -> 2;
            case NOT_CHECKED -> 1;
        };
    }
}
