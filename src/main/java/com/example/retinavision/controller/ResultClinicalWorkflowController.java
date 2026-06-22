package com.example.retinavision.controller;

import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.pojo.DTO.SubmitFeedbackDTO;
import com.example.retinavision.pojo.DTO.SubmitReviewDTO;
import com.example.retinavision.pojo.DTO.UpdateReportDraftDTO;
import com.example.retinavision.pojo.Entity.*;
import com.example.retinavision.pojo.VO.ClinicalWorkflowVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/analysis-results/{resultId}")
public class ResultClinicalWorkflowController {
    private final ResultHumanWorkflowService humanService;
    private final ResultReviewService reviewService;
    private final AnalysisReportService reportService;
    private final ClinicalAccessService accessService;

    public ResultClinicalWorkflowController(ResultHumanWorkflowService humanService,
                                            ResultReviewService reviewService,
                                            AnalysisReportService reportService,
                                            ClinicalAccessService accessService) {
        this.humanService = humanService;
        this.reviewService = reviewService;
        this.reportService = reportService;
        this.accessService = accessService;
    }

    @PostMapping("/feedback")
    public Result<ClinicalWorkflowVO.Feedback> feedback(@PathVariable Long resultId,
                                                        @RequestBody SubmitFeedbackDTO body,
                                                        Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(feedback(humanService.addFeedback(resultId, body, user.getId())));
    }

    @GetMapping("/feedback")
    public Result<List<ClinicalWorkflowVO.Feedback>> feedbackList(@PathVariable Long resultId, Authentication authentication) {
        authorize(authentication, resultId);
        return Result.success(humanService.listFeedback(resultId).stream().map(this::feedback).toList());
    }

    @PostMapping(value = "/corrections", consumes = "multipart/form-data")
    public Result<ClinicalWorkflowVO.Correction> correction(@PathVariable Long resultId,
                                                            @RequestPart("file") MultipartFile file,
                                                            @RequestParam String reason,
                                                            @RequestParam(required = false) String correctedResultJson,
                                                            @RequestParam Integer expectedVersion,
                                                            Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(correction(humanService.addCorrection(resultId, file, reason,
                correctedResultJson, expectedVersion, user.getId())));
    }

    @GetMapping("/corrections")
    public Result<List<ClinicalWorkflowVO.Correction>> correctionList(@PathVariable Long resultId, Authentication authentication) {
        authorize(authentication, resultId);
        return Result.success(humanService.listCorrections(resultId).stream().map(this::correction).toList());
    }

    @GetMapping("/corrections/{version}")
    public Result<ClinicalWorkflowVO.Correction> correction(@PathVariable Long resultId,
                                                            @PathVariable Integer version,
                                                            Authentication authentication) {
        authorize(authentication, resultId);
        return Result.success(correction(humanService.getCorrection(resultId, version)));
    }

    @GetMapping("/review")
    public Result<ClinicalWorkflowVO.Review> review(@PathVariable Long resultId, Authentication authentication) {
        authorize(authentication, resultId);
        return Result.success(review(reviewService.getReview(resultId)));
    }

    @PostMapping("/review")
    public Result<ClinicalWorkflowVO.Review> review(@PathVariable Long resultId,
                                                    @RequestBody SubmitReviewDTO body,
                                                    Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(review(reviewService.review(resultId, body, user.getId())));
    }

    @GetMapping("/report-draft")
    public Result<ClinicalWorkflowVO.Report> draft(@PathVariable Long resultId, Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(report(reportService.getOrCreateDraft(resultId, user.getId())));
    }

    @PutMapping("/report-draft")
    public Result<ClinicalWorkflowVO.Report> draft(@PathVariable Long resultId,
                                                   @RequestBody UpdateReportDraftDTO body,
                                                   Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(report(reportService.updateDraft(resultId, body, user.getId())));
    }

    @PostMapping("/report-sign")
    public Result<ClinicalWorkflowVO.Report> sign(@PathVariable Long resultId, Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(report(reportService.sign(resultId, user.getId())));
    }

    @GetMapping("/reports")
    public Result<List<ClinicalWorkflowVO.Report>> reports(@PathVariable Long resultId, Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(reportService.list(resultId).stream()
                .filter(item -> user.getRoleCode() == UserRole.DOCTOR
                        || item.getStatus() == ReportStatus.SIGNED
                        || item.getStatus() == ReportStatus.SUPERSEDED)
                .map(this::report).toList());
    }

    @GetMapping("/reports/{version}")
    public ResponseEntity<InputStreamResource> report(@PathVariable Long resultId,
                                                      @PathVariable Integer version,
                                                      Authentication authentication) throws java.io.IOException {
        CurrentUserVO user = authorize(authentication, resultId);
        AnalysisReportEntity selected = reportService.list(resultId).stream()
                .filter(item -> item.getVersion().equals(version)).findFirst()
                .orElseThrow(() -> new com.example.retinavision.exception.BaseException(40400, "报告不存在"));
        if (selected.getStatus() != ReportStatus.SIGNED
                && selected.getStatus() != ReportStatus.SUPERSEDED) {
            throw new com.example.retinavision.exception.BaseException(40400, "报告不存在");
        }
        Path path = reportService.getFile(resultId, version);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + resultId + "-v" + version + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(Files.newInputStream(path)));
    }

    private CurrentUserVO authorize(Authentication authentication, Long resultId) {
        CurrentUserVO user = (CurrentUserVO) authentication.getPrincipal();
        accessService.assertCanAccessResult(user, resultId);
        return user;
    }

    private ClinicalWorkflowVO.Feedback feedback(AnalysisFeedbackEntity x) { return new ClinicalWorkflowVO.Feedback(x.getId(), x.getVerdict(), x.getIssueCodes(), x.getComment(), x.getSubmittedBy(), x.getCreatedAt()); }
    private ClinicalWorkflowVO.Correction correction(AnalysisCorrectionEntity x) { return new ClinicalWorkflowVO.Correction(x.getId(), x.getVersion(), x.getCorrectedResultJson(), x.getCorrectedMaskObjectKey(), x.getReason(), x.getStatus(), x.getSubmittedBy(), x.getCreatedAt(), x.getUpdatedAt()); }
    private ClinicalWorkflowVO.Review review(AnalysisReviewEntity x) { return x == null ? null : new ClinicalWorkflowVO.Review(x.getCorrectionVersion(), x.getStatus(), x.getFindings(), x.getConclusion(), x.getRecommendation(), x.getReviewerNameSnapshot(), x.getProfessionalNoSnapshot(), x.getVersion(), x.getReviewedAt()); }
    private ClinicalWorkflowVO.Report report(AnalysisReportEntity x) { return new ClinicalWorkflowVO.Report(x.getVersion(), x.getStatus(), x.getCorrectionVersion(), x.getDraftJson(), x.getReportSha256(), x.getSignerNameSnapshot(), x.getProfessionalNoSnapshot(), x.getSignedAt(), x.getCreatedAt(), x.getUpdatedAt()); }
}
