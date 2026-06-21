package com.example.retinavision.controller;

import com.example.retinavision.pojo.DTO.SubmitFeedbackDTO;
import com.example.retinavision.pojo.DTO.SubmitReviewDTO;
import com.example.retinavision.pojo.Entity.AnalysisCorrectionEntity;
import com.example.retinavision.pojo.Entity.AnalysisFeedbackEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.ResultHumanWorkflowService;
import com.example.retinavision.service.ResultReviewService;
import com.example.retinavision.service.AnalysisReportService;
import com.example.retinavision.pojo.DTO.UpdateReportDraftDTO;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import java.nio.file.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import com.example.retinavision.pojo.VO.ClinicalWorkflowVO;

@RestController @RequestMapping("/analysis-results/{resultId}")
public class ResultClinicalWorkflowController {
    private final ResultHumanWorkflowService humanService; private final ResultReviewService reviewService; private final AnalysisReportService reportService;
    public ResultClinicalWorkflowController(ResultHumanWorkflowService humanService,ResultReviewService reviewService,AnalysisReportService reportService){this.humanService=humanService;this.reviewService=reviewService;this.reportService=reportService;}
    @PostMapping("/feedback") public Result<ClinicalWorkflowVO.Feedback> feedback(@PathVariable Long resultId,@RequestBody SubmitFeedbackDTO body,Authentication a){return Result.success(feedback(humanService.addFeedback(resultId,body,user(a).getId())));}
    @GetMapping("/feedback") public Result<List<ClinicalWorkflowVO.Feedback>> feedbackList(@PathVariable Long resultId){return Result.success(humanService.listFeedback(resultId).stream().map(this::feedback).toList());}
    @PostMapping(value="/corrections",consumes="multipart/form-data") public Result<ClinicalWorkflowVO.Correction> correction(@PathVariable Long resultId,@RequestPart("file") MultipartFile file,@RequestParam String reason,@RequestParam(required=false) String correctedResultJson,@RequestParam Integer expectedVersion,Authentication a){return Result.success(correction(humanService.addCorrection(resultId,file,reason,correctedResultJson,expectedVersion,user(a).getId())));}
    @GetMapping("/corrections") public Result<List<ClinicalWorkflowVO.Correction>> correctionList(@PathVariable Long resultId){return Result.success(humanService.listCorrections(resultId).stream().map(this::correction).toList());}
    @GetMapping("/corrections/{version}") public Result<ClinicalWorkflowVO.Correction> correction(@PathVariable Long resultId,@PathVariable Integer version){return Result.success(correction(humanService.getCorrection(resultId,version)));}
    @GetMapping("/review") public Result<ClinicalWorkflowVO.Review> review(@PathVariable Long resultId){return Result.success(review(reviewService.getReview(resultId)));}
    @PostMapping("/review") public Result<ClinicalWorkflowVO.Review> review(@PathVariable Long resultId,@RequestBody SubmitReviewDTO body,Authentication a){return Result.success(review(reviewService.review(resultId,body,user(a).getId())));}
    @GetMapping("/report-draft") public Result<ClinicalWorkflowVO.Report> draft(@PathVariable Long resultId,Authentication a){return Result.success(report(reportService.getOrCreateDraft(resultId,user(a).getId())));}
    @PutMapping("/report-draft") public Result<ClinicalWorkflowVO.Report> draft(@PathVariable Long resultId,@RequestBody UpdateReportDraftDTO body,Authentication a){return Result.success(report(reportService.updateDraft(resultId,body,user(a).getId())));}
    @PostMapping("/report-sign") public Result<ClinicalWorkflowVO.Report> sign(@PathVariable Long resultId,Authentication a){return Result.success(report(reportService.sign(resultId,user(a).getId())));}
    @GetMapping("/reports") public Result<List<ClinicalWorkflowVO.Report>> reports(@PathVariable Long resultId){return Result.success(reportService.list(resultId).stream().map(this::report).toList());}
    @GetMapping("/reports/{version}") public ResponseEntity<InputStreamResource> report(@PathVariable Long resultId,@PathVariable Integer version) throws java.io.IOException {Path p=reportService.getFile(resultId,version);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=report-"+resultId+"-v"+version+".pdf").contentType(MediaType.APPLICATION_PDF).body(new InputStreamResource(Files.newInputStream(p)));}
    private CurrentUserVO user(Authentication a){return (CurrentUserVO)a.getPrincipal();}
    private ClinicalWorkflowVO.Feedback feedback(AnalysisFeedbackEntity x){return new ClinicalWorkflowVO.Feedback(x.getId(),x.getVerdict(),x.getIssueCodes(),x.getComment(),x.getSubmittedBy(),x.getCreatedAt());}
    private ClinicalWorkflowVO.Correction correction(AnalysisCorrectionEntity x){return new ClinicalWorkflowVO.Correction(x.getId(),x.getVersion(),x.getCorrectedResultJson(),x.getCorrectedMaskObjectKey(),x.getReason(),x.getStatus(),x.getSubmittedBy(),x.getCreatedAt(),x.getUpdatedAt());}
    private ClinicalWorkflowVO.Review review(AnalysisReviewEntity x){return x==null?null:new ClinicalWorkflowVO.Review(x.getCorrectionVersion(),x.getStatus(),x.getFindings(),x.getConclusion(),x.getRecommendation(),x.getReviewerNameSnapshot(),x.getProfessionalNoSnapshot(),x.getVersion(),x.getReviewedAt());}
    private ClinicalWorkflowVO.Report report(AnalysisReportEntity x){return new ClinicalWorkflowVO.Report(x.getVersion(),x.getStatus(),x.getCorrectionVersion(),x.getDraftJson(),x.getReportSha256(),x.getSignerNameSnapshot(),x.getProfessionalNoSnapshot(),x.getSignedAt(),x.getCreatedAt(),x.getUpdatedAt());}
}
