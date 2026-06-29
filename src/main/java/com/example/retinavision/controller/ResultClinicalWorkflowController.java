package com.example.retinavision.controller;

import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.DTO.SubmitFeedbackDTO;
import com.example.retinavision.pojo.DTO.SubmitReviewDTO;
import com.example.retinavision.pojo.DTO.UpdateReportDraftDTO;
import com.example.retinavision.pojo.Entity.AnalysisCorrectionEntity;
import com.example.retinavision.pojo.Entity.AnalysisFeedbackEntity;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.VO.ClinicalWorkflowVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.AnalysisReportService;
import com.example.retinavision.service.ClinicalAccessService;
import com.example.retinavision.service.ResultHumanWorkflowService;
import com.example.retinavision.service.ResultReviewService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 分析结果临床闭环 Controller。
 *
 * <p>这组接口围绕一个 resultId 展开，覆盖：
 * 人工反馈 → 人工修正版本 → 医生审核 → 报告草稿 → PDF 签发/下载。</p>
 *
 * <p>Controller 只做三件事：
 * 1. 从 HTTP 请求中取参数；
 * 2. 调用 {@link ClinicalAccessService} 做对象级权限校验；
 * 3. 把 Service 返回的 Entity 转成前端需要的 VO。</p>
 */
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

    /**
     * 提交人工反馈。
     *
     * <p>反馈不改变 AI 原始结果，也不等于医生审核通过；
     * 它只是给后续医生判断和算法改进提供记录。</p>
     */
    @PostMapping("/feedback")
    public Result<ClinicalWorkflowVO.Feedback> feedback(@PathVariable Long resultId,
                                                        @RequestBody SubmitFeedbackDTO body,
                                                        Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(toFeedback(humanService.addFeedback(resultId, body, user.getId())));
    }

    /**
     * 查看某个分析结果下的所有人工反馈。
     */
    @GetMapping("/feedback")
    public Result<List<ClinicalWorkflowVO.Feedback>> feedbackList(@PathVariable Long resultId,
                                                                  Authentication authentication) {
        authorize(authentication, resultId);
        return Result.success(humanService.listFeedback(resultId).stream().map(this::toFeedback).toList());
    }

    /**
     * 上传人工修正 mask。
     *
     * <p>修正版本采用追加模型：不会覆盖 AI 原始 mask。
     * expectedVersion 用来防止两个人同时上传时生成冲突版本。</p>
     */
    //consumes = "multipart/form-data" 是为了让 Spring MVC 正确解析 multipart/form-data 请求。
    @PostMapping(value = "/corrections", consumes = "multipart/form-data")
    //@RequestPart 用于接收 multipart/form-data 请求中的文件部分，Spring MVC 会自动将上传的文件封装为 MultipartFile 对象。
    //@RequestParam 用于接收请求中的普通参数。
    public Result<ClinicalWorkflowVO.Correction> correction(@PathVariable Long resultId,
                                                            @RequestPart("file") MultipartFile file,
                                                            @RequestParam String reason,
                                                            @RequestParam(required = false) String correctedResultJson,
                                                            @RequestParam Integer expectedVersion,
                                                            Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(toCorrection(humanService.addCorrection(
                resultId,
                file,
                reason,
                correctedResultJson,
                expectedVersion,
                user.getId()
        )));
    }

    /**
     * 查询某个分析结果的全部人工修正版本。
     */
    @GetMapping("/corrections")
    public Result<List<ClinicalWorkflowVO.Correction>> correctionList(@PathVariable Long resultId,
                                                                      Authentication authentication) {
        authorize(authentication, resultId);
        return Result.success(humanService.listCorrections(resultId).stream().map(this::toCorrection).toList());
    }

    /**
     * 查询某一个具体修正版本。
     */
    @GetMapping("/corrections/{version}")
    public Result<ClinicalWorkflowVO.Correction> correction(@PathVariable Long resultId,
                                                            @PathVariable Integer version,
                                                            Authentication authentication) {
        authorize(authentication, resultId);
        return Result.success(toCorrection(humanService.getCorrection(resultId, version)));
    }

    /**
     * 查看医生审核记录。
     */
    @GetMapping("/review")
    public Result<ClinicalWorkflowVO.Review> review(@PathVariable Long resultId, Authentication authentication) {
        authorize(authentication, resultId);
        return Result.success(toReview(reviewService.getReview(resultId)));
    }

    /**
     * 保存医生审核结论。
     *
     * <p>真正的权限控制应由 Spring Security 和 ClinicalAccessService 保证：
     * 只有医生可以最终审核，且只能审核自己有权访问的临床结果。</p>
     */
    @PostMapping("/review")
    public Result<ClinicalWorkflowVO.Review> review(@PathVariable Long resultId,
                                                    @RequestBody SubmitReviewDTO body,
                                                    Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(toReview(reviewService.review(resultId, body, user.getId())));
    }

    /**
     * 获取或创建报告草稿。
     *
     * <p>草稿只是医生编辑区，不代表正式报告，普通用户不应该下载草稿。</p>
     */
    @GetMapping("/report-draft")
    public Result<ClinicalWorkflowVO.Report> draft(@PathVariable Long resultId, Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(toReport(reportService.getOrCreateDraft(resultId, user.getId())));
    }

    /**
     * 更新报告草稿。
     */
    @PutMapping("/report-draft")
    public Result<ClinicalWorkflowVO.Report> draft(@PathVariable Long resultId,
                                                   @RequestBody UpdateReportDraftDTO body,
                                                   Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(toReport(reportService.updateDraft(resultId, body, user.getId())));
    }

    /**
     * 医生签发正式 PDF 报告。
     *
     * <p>签发前 Service 会检查审核状态必须为 APPROVED；
     * 签发后会生成 PDF、计算 SHA-256，并保存医生身份快照。</p>
     */
    @PostMapping("/report-sign")
    public Result<ClinicalWorkflowVO.Report> sign(@PathVariable Long resultId, Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(toReport(reportService.sign(resultId, user.getId())));
    }

    /**
     * 查询报告版本列表。
     *
     * <p>医生可以看到 DRAFT；非医生只能看到 SIGNED/SUPERSEDED，
     * 避免普通用户下载未审核、未签发的草稿。</p>
     */
    @GetMapping("/reports")
    public Result<List<ClinicalWorkflowVO.Report>> reports(@PathVariable Long resultId,
                                                           Authentication authentication) {
        CurrentUserVO user = authorize(authentication, resultId);
        return Result.success(reportService.list(resultId).stream()
                .filter(item -> user.getRoleCode() == UserRole.DOCTOR
                        || item.getStatus() == ReportStatus.SIGNED
                        || item.getStatus() == ReportStatus.SUPERSEDED)
                .map(this::toReport)
                .toList());
    }

    /**
     * 下载指定版本的正式 PDF 报告。
     *
     * <p>这里只允许 SIGNED/SUPERSEDED 下载；DRAFT 会被当作不存在处理。</p>
     */
    @GetMapping("/reports/{version}")
    public ResponseEntity<InputStreamResource> report(@PathVariable Long resultId,
                                                      @PathVariable Integer version,
                                                      Authentication authentication) throws java.io.IOException {
        authorize(authentication, resultId);

        AnalysisReportEntity selected = reportService.list(resultId).stream()
                .filter(item -> item.getVersion().equals(version))
                .findFirst()
                .orElseThrow(() -> new BaseException(40400, "报告不存在"));
        if (selected.getStatus() != ReportStatus.SIGNED
                && selected.getStatus() != ReportStatus.SUPERSEDED) {
            throw new BaseException(40400, "报告不存在");
        }

        Path path = reportService.getFile(resultId, version);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + resultId + "-v" + version + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(Files.newInputStream(path)));
    }

    /**
     * 统一对象级权限校验入口。
     *
     * <p> 只要接口 URL 带 resultId，都先调用这里，防止用户猜测其他人的 resultId 读取临床数据。</p>
     * */
    private CurrentUserVO authorize(Authentication authentication, Long resultId) {
        CurrentUserVO user = (CurrentUserVO) authentication.getPrincipal();
        accessService.assertCanAccessResult(user, resultId);
        return user;
    }

    private ClinicalWorkflowVO.Feedback toFeedback(AnalysisFeedbackEntity entity) {
        return new ClinicalWorkflowVO.Feedback(
                entity.getId(),
                entity.getVerdict(),
                entity.getIssueCodes(),
                entity.getComment(),
                entity.getSubmittedBy(),
                entity.getCreatedAt()
        );
    }

    private ClinicalWorkflowVO.Correction toCorrection(AnalysisCorrectionEntity entity) {
        return new ClinicalWorkflowVO.Correction(
                entity.getId(),
                entity.getVersion(),
                entity.getCorrectedResultJson(),
                entity.getCorrectedMaskObjectKey(),
                entity.getReason(),
                entity.getStatus(),
                entity.getSubmittedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ClinicalWorkflowVO.Review toReview(AnalysisReviewEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ClinicalWorkflowVO.Review(
                entity.getCorrectionVersion(),
                entity.getStatus(),
                entity.getFindings(),
                entity.getConclusion(),
                entity.getRecommendation(),
                entity.getReviewerNameSnapshot(),
                entity.getProfessionalNoSnapshot(),
                entity.getVersion(),
                entity.getReviewedAt()
        );
    }

    private ClinicalWorkflowVO.Report toReport(AnalysisReportEntity entity) {
        return new ClinicalWorkflowVO.Report(
                entity.getVersion(),
                entity.getStatus(),
                entity.getCorrectionVersion(),
                entity.getDraftJson(),
                entity.getReportSha256(),
                entity.getSignerNameSnapshot(),
                entity.getProfessionalNoSnapshot(),
                entity.getSignedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
