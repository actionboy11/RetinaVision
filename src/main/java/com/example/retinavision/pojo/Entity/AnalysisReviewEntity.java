package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.ReviewStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * analysis_review 表实体。
 *
 * <p>含义：医生对一个 AI 分析结果的审核结论。
 * 一个 resultId 当前只保留一条审核记录，通过 version 字段做乐观锁。</p>
 */
@Data
@TableName("analysis_review")
public class AnalysisReviewEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被审核的 AI 分析结果 ID。
     */
    private Long resultId;

    /**
     * 本次审核采用的人工修正版本。
     * 为空表示直接审核 AI 原始结果。
     */
    private Integer correctionVersion;

    /**
     * 医生审核状态。
     */
    private ReviewStatus status;

    /**
     * 医生所见。
     */
    private String findings;

    /**
     * 医生结论。
     */
    private String conclusion;

    /**
     * 医生建议。
     */
    private String recommendation;

    /**
     * 审核医生的用户 ID。
     */
    private Integer reviewerId;

    /**
     * 医生姓名快照。
     * 使用快照是为了防止医生后来改名后，历史报告署名也跟着变化。
     */
    private String reviewerNameSnapshot;

    /**
     * 医生执业/工号快照。
     */
    private String professionalNoSnapshot;

    /**
     * 乐观锁版本号，防止并发审核互相覆盖。
     */
    private Integer version;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
