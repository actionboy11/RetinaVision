package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.CorrectionStatus;
import com.example.retinavision.enumeration.FeedbackVerdict;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.ReviewStatus;

import java.time.LocalDateTime;

/**
 * 临床闭环相关接口返回给前端的 VO 集合。
 *
 * <p>这里使用 Java record，是因为这些对象只负责“把后端状态投影给页面”，
 * 不承载业务行为。ResultClinicalWorkflowController 会把 Entity 转成这些 VO。</p>
 */
public final class ClinicalWorkflowVO {

    private ClinicalWorkflowVO() {
    }

    /**
     * 人工反馈展示对象。
     */
    public record Feedback(
            Long id,
            FeedbackVerdict verdict,
            String issueCodes,
            String comment,
            Integer submittedBy,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 人工修正版本展示对象。
     *
     * <p>注意：correctedMaskObjectKey 是后端内部对象路径，不应由前端直接拼文件路径；
     * 下载或预览仍应通过后端受控接口完成。</p>
     */
    public record Correction(
            Long id,
            Integer version,
            String correctedResultJson,
            String correctedMaskObjectKey,
            String reason,
            CorrectionStatus status,
            Integer submittedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * 医生审核展示对象。
     */
    public record Review(
            Integer correctionVersion,
            ReviewStatus status,
            String findings,
            String conclusion,
            String recommendation,
            String reviewerNameSnapshot,
            String professionalNoSnapshot,
            Integer version,
            LocalDateTime reviewedAt
    ) {
    }

    /**
     * 报告版本展示对象。
     *
     * <p>DRAFT 只能医生看到；普通用户只应该看到 SIGNED 或 SUPERSEDED 报告。</p>
     */
    public record Report(
            Integer version,
            ReportStatus status,
            Integer correctionVersion,
            String draftJson,
            String reportSha256,
            String signerNameSnapshot,
            String professionalNoSnapshot,
            LocalDateTime signedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
