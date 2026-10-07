package com.example.retinavision.agent.evaluation;

import com.example.retinavision.agent.PatientCaseSearchCriteria;
import com.example.retinavision.agent.PatientQualityDisplayStatus;
import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.pojo.VO.*;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.PatientAgentQueryService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FixturePatientAgentQueryService implements PatientAgentQueryService {
    private static final int MAX_PAGE_SIZE = 10;
    private final List<PatientAgentCaseSummaryVO> cases = createCases();

    @Override
    public PageResult<PatientAgentCaseSummaryVO> listMyCases(PatientCaseSearchCriteria criteria,
                                                             int page, int pageSize, CurrentUserVO patient) {
        requirePatient(patient);
        List<PatientAgentCaseSummaryVO> filtered = cases.stream()
                .filter(item -> !criteria.reuploadOnly()
                        || item.getQualityStatus() == PatientQualityDisplayStatus.REUPLOAD_RECOMMENDED)
                .filter(item -> !criteria.signedReportOnly() || item.isSignedReportAvailable()).toList();
        return page(filtered, page, pageSize);
    }

    @Override
    public PatientAgentProgressVO getMyCaseProgress(String caseReference, CurrentUserVO patient) {
        requirePatient(patient);
        PatientAgentCaseSummaryVO found = findCase(caseReference);
        PatientAgentProgressVO progress = new PatientAgentProgressVO();
        progress.setCaseId(found.getCaseId());
        progress.setCaseNo(found.getCaseNo());
        progress.setCurrentStage(found.isSignedReportAvailable() ? "FORMAL_REPORT" : "DOCTOR_PROCESSING");
        progress.setNextHandler(found.isSignedReportAvailable() ? "已完成" : "负责医生");
        progress.setQualityStatus(found.getQualityStatus());
        progress.setQualityMessage(found.getQualityMessage());
        progress.setStages(List.of());
        progress.setUpdatedAt(found.getUpdatedAt());
        return progress;
    }

    @Override
    public PageResult<PatientAgentSignedReportVO> listMySignedReports(String caseReference,
                                                                     int page, int pageSize,
                                                                     CurrentUserVO patient) {
        requirePatient(patient);
        List<PatientAgentSignedReportVO> reports = cases.stream().filter(PatientAgentCaseSummaryVO::isSignedReportAvailable)
                .filter(item -> caseReference == null || caseReference.isBlank()
                        || item.getCaseNo().equalsIgnoreCase(caseReference)
                        || String.valueOf(item.getCaseId()).equals(caseReference))
                .map(this::report).toList();
        return page(reports, page, pageSize);
    }

    @Override
    public PatientAgentReportDetailVO getMySignedReport(String caseReference, Long resultId,
                                                         Integer version, CurrentUserVO patient) {
        requirePatient(patient);
        PatientAgentCaseSummaryVO found = findCase(caseReference);
        if (!found.isSignedReportAvailable() || !Long.valueOf(found.getCaseId() + 50_000).equals(resultId)) {
            throw new IllegalArgumentException("评测报告不存在");
        }
        PatientAgentReportDetailVO detail = new PatientAgentReportDetailVO();
        detail.setCaseId(found.getCaseId());
        detail.setCaseNo(found.getCaseNo());
        detail.setResultId(resultId);
        detail.setVersion(version == null ? 1 : version);
        detail.setFindings("匿名评测所见");
        detail.setConclusion("匿名评测结论");
        detail.setRecommendation("请遵循负责医生建议");
        return detail;
    }

    private PatientAgentCaseSummaryVO findCase(String reference) {
        return cases.stream().filter(item -> item.getCaseNo().equalsIgnoreCase(reference)
                        || String.valueOf(item.getCaseId()).equals(reference))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("评测病例不存在"));
    }

    private PatientAgentSignedReportVO report(PatientAgentCaseSummaryVO item) {
        PatientAgentSignedReportVO report = new PatientAgentSignedReportVO();
        report.setCaseId(item.getCaseId());
        report.setCaseNo(item.getCaseNo());
        report.setResultId(item.getCaseId() + 50_000);
        report.setVersion(1);
        report.setSignerName("评测医生");
        return report;
    }

    private <T> PageResult<T> page(List<T> values, int requestedPage, int requestedSize) {
        int page = Math.max(1, requestedPage);
        int size = Math.min(MAX_PAGE_SIZE, Math.max(1, requestedSize));
        int from = Math.min(values.size(), (page - 1) * size);
        int to = Math.min(values.size(), from + size);
        return new PageResult<>(new ArrayList<>(values.subList(from, to)), values.size(), page, size);
    }

    private void requirePatient(CurrentUserVO user) {
        if (user == null || user.getRoleCode() != UserRole.USER) {
            throw new IllegalArgumentException("评测患者身份无效");
        }
    }

    private List<PatientAgentCaseSummaryVO> createCases() {
        List<PatientAgentCaseSummaryVO> values = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            PatientAgentCaseSummaryVO item = new PatientAgentCaseSummaryVO();
            item.setCaseId(40_000L + index);
            item.setCaseNo("EVAL-C-P-%03d".formatted(index));
            item.setEyeSide(index % 2 == 0 ? "RIGHT" : "LEFT");
            item.setWorkflowStatus(index <= 2 ? CaseWorkflowStatus.IN_REVIEW : CaseWorkflowStatus.COMPLETED);
            item.setQualityStatus(index == 1 ? PatientQualityDisplayStatus.REUPLOAD_RECOMMENDED
                    : PatientQualityDisplayStatus.ACCEPTABLE);
            item.setQualityMessage(index == 1 ? "图像清晰度不足，建议重新上传" : "图像质量可用");
            item.setSignedReportAvailable(index >= 3);
            item.setUpdatedAt(LocalDateTime.of(2026, 10, 7, 10, 0).minusMinutes(index));
            values.add(item);
        }
        return List.copyOf(values);
    }
}
