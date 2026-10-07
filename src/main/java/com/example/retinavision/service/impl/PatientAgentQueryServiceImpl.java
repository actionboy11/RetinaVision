package com.example.retinavision.service.impl;

import com.example.retinavision.agent.PatientCaseSearchCriteria;
import com.example.retinavision.agent.PatientQualityDisplayStatus;
import com.example.retinavision.agent.PatientQualityPresenter;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.PatientAgentQueryMapper;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PatientAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.PatientAgentProgressStageVO;
import com.example.retinavision.pojo.VO.PatientAgentProgressVO;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;
import com.example.retinavision.pojo.VO.PatientAgentSignedReportVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.PatientAgentQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PatientAgentQueryServiceImpl implements PatientAgentQueryService {
    private static final int PAGE_SIZE = 10;
    private final PatientAgentQueryMapper mapper;
    private final PatientQualityPresenter qualityPresenter;

    public PatientAgentQueryServiceImpl(PatientAgentQueryMapper mapper,
                                        PatientQualityPresenter qualityPresenter) {
        this.mapper = mapper;
        this.qualityPresenter = qualityPresenter;
    }

    @Override
    public PageResult<PatientAgentCaseSummaryVO> listMyCases(PatientCaseSearchCriteria criteria,
                                                              int page, int pageSize,
                                                              CurrentUserVO patient) {
        requirePatient(patient);
        PatientCaseSearchCriteria safeCriteria = criteria == null
                ? new PatientCaseSearchCriteria(false, false) : criteria;
        int safePage = Math.max(page, 1);
        int safeSize = pageSize < 1 ? PAGE_SIZE : Math.min(pageSize, PAGE_SIZE);
        long total = mapper.countMyCases(patient.getId(), safeCriteria);
        var projections = mapper.selectMyCases(patient.getId(), safeCriteria,
                (safePage - 1) * safeSize, safeSize);
        List<PatientAgentCaseSummaryVO> records = projections == null
                ? List.of() : projections.stream().map(this::toSummary).toList();
        return new PageResult<>(records, total, safePage, safeSize);
    }

    @Override
    public PatientAgentProgressVO getMyCaseProgress(String caseReference, CurrentUserVO patient) {
        requirePatient(patient);
        String reference = normalizeReference(caseReference);
        var projection = mapper.selectMyCaseProgress(patient.getId(), reference, parseId(reference));
        if (projection == null) throw notFound();
        return toProgress(projection);
    }

    @Override
    public PageResult<PatientAgentSignedReportVO> listMySignedReports(String caseReference,
                                                                      int page, int pageSize,
                                                                      CurrentUserVO patient) {
        requirePatient(patient);
        String reference = normalizeReference(caseReference);
        Long caseId = parseId(reference);
        int safePage = Math.max(page, 1);
        int safeSize = pageSize < 1 ? PAGE_SIZE : Math.min(pageSize, PAGE_SIZE);
        long total = mapper.countMySignedReports(patient.getId(), reference, caseId);
        var records = mapper.selectMySignedReports(patient.getId(), reference, caseId,
                (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(records == null ? List.of() : records, total, safePage, safeSize);
    }

    @Override
    public PatientAgentReportDetailVO getMySignedReport(String caseReference, Long resultId,
                                                         Integer version, CurrentUserVO patient) {
        requirePatient(patient);
        String reference = normalizeReference(caseReference);
        PatientAgentReportDetailVO detail = mapper.selectMySignedReport(patient.getId(), reference,
                parseId(reference), resultId, version);
        if (detail == null) throw notFound();
        return detail;
    }

    private PatientAgentCaseSummaryVO toSummary(PatientAgentQueryMapper.CaseProjection source) {
        var quality = qualityPresenter.present(source.getQualityStatus(), source.getQualityReason());
        PatientAgentCaseSummaryVO target = new PatientAgentCaseSummaryVO();
        target.setCaseId(source.getCaseId());
        target.setCaseNo(source.getCaseNo());
        target.setEyeSide(source.getEyeSide());
        target.setWorkflowStatus(parseWorkflow(source.getWorkflowStatus()));
        target.setQualityStatus(quality.status());
        target.setQualityMessage(quality.message());
        target.setSignedReportAvailable(source.getSignedReportCount() != null
                && source.getSignedReportCount() > 0);
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private PatientAgentProgressVO toProgress(PatientAgentQueryMapper.ProgressProjection source) {
        var quality = qualityPresenter.present(source.getQualityStatus(), source.getQualityReason());
        int imageCount = source.getImageCount() == null ? 0 : source.getImageCount();
        int reportCount = source.getSignedReportCount() == null ? 0 : source.getSignedReportCount();
        String current = currentStage(imageCount, quality.status(), source.getAnalysisStatus(), reportCount);
        PatientAgentProgressVO result = new PatientAgentProgressVO();
        result.setCaseId(source.getCaseId());
        result.setCaseNo(source.getCaseNo());
        result.setCurrentStage(current);
        result.setStages(stages(current, reportCount > 0));
        result.setQualityStatus(quality.status());
        result.setQualityMessage(quality.message());
        result.setNextHandler(nextHandler(current, quality.status()));
        result.setMessage(stageMessage(current));
        result.setUpdatedAt(source.getUpdatedAt());
        result.setEstimatedCompletionAt(null);
        return result;
    }

    private String currentStage(int imageCount, PatientQualityDisplayStatus quality,
                                String analysisStatus, int reportCount) {
        if (imageCount == 0) return "IMAGE_UPLOAD";
        if (quality == PatientQualityDisplayStatus.CHECKING
                || quality == PatientQualityDisplayStatus.REUPLOAD_RECOMMENDED
                || quality == PatientQualityDisplayStatus.UNAVAILABLE) return "QUALITY_CHECK";
        if (reportCount > 0) return "FORMAL_REPORT";
        if ("SUCCESS".equals(analysisStatus)) return "FORMAL_REPORT";
        return "DOCTOR_PROCESSING";
    }

    private List<PatientAgentProgressStageVO> stages(String current, boolean reportSigned) {
        List<String> codes = List.of("IMAGE_UPLOAD", "QUALITY_CHECK", "DOCTOR_PROCESSING", "FORMAL_REPORT");
        List<String> labels = List.of("已上传图像", "图像质量检查", "医生处理", "正式报告");
        int currentIndex = codes.indexOf(current);
        List<PatientAgentProgressStageVO> stages = new ArrayList<>();
        for (int index = 0; index < codes.size(); index++) {
            String status = index < currentIndex || (reportSigned && index == currentIndex)
                    ? "COMPLETED" : index == currentIndex ? "CURRENT" : "PENDING";
            stages.add(new PatientAgentProgressStageVO(codes.get(index), labels.get(index), status));
        }
        return List.copyOf(stages);
    }

    private String nextHandler(String stage, PatientQualityDisplayStatus quality) {
        return switch (stage) {
            case "IMAGE_UPLOAD" -> "患者";
            case "QUALITY_CHECK" -> quality == PatientQualityDisplayStatus.CHECKING ? "系统" : "患者";
            case "DOCTOR_PROCESSING", "FORMAL_REPORT" -> "负责医生";
            default -> null;
        };
    }

    private String stageMessage(String stage) {
        return switch (stage) {
            case "IMAGE_UPLOAD" -> "请先上传检查图像";
            case "QUALITY_CHECK" -> "请查看图像质量提示";
            case "DOCTOR_PROCESSING" -> "检查已交由负责医生处理";
            case "FORMAL_REPORT" -> "请查看正式报告状态";
            default -> "检查状态已更新";
        };
    }

    private CaseWorkflowStatus parseWorkflow(String value) {
        try {
            return value == null ? null : CaseWorkflowStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Long parseId(String reference) {
        if (reference == null || !reference.matches("\\d+")) return null;
        try {
            return Long.valueOf(reference);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeReference(String reference) {
        return reference == null ? "" : reference.trim();
    }

    private void requirePatient(CurrentUserVO patient) {
        if (patient == null || patient.getRoleCode() != UserRole.USER) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权使用患者检查查询能力");
        }
    }

    private BaseException notFound() {
        return new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
    }
}
