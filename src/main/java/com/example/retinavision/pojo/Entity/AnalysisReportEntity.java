package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.ReportStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * analysis_report 表实体。
 *
 * <p>含义：分析报告的草稿、签发版本和历史版本。
 * DRAFT 可以修改；SIGNED/SUPERSEDED 是留档版本，不应被覆盖。</p>
 */
@Data
@TableName("analysis_report")
public class AnalysisReportEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 报告基于哪个 AI 分析结果生成。
     */
    private Long resultId;

    /**
     * 同一 resultId 下的报告版本号。
     */
    private Integer version;

    /**
     * 报告状态：草稿、已签发、已被新版本替代。
     */
    private ReportStatus status;

    /**
     * 报告采用的人工修正版本。
     * 为空表示采用 AI 原始结果。
     */
    private Integer correctionVersion;

    /**
     * 报告草稿 JSON，保存医生填写的所见、结论、建议等结构化内容。
     */
    private String draftJson;

    /**
     * 已签发 PDF 在结果目录下的对象 key。
     */
    private String reportObjectKey;

    /**
     * 已签发 PDF 的 SHA-256，用于校验文件是否被篡改。
     */
    private String reportSha256;

    /**
     * 创建草稿的用户 ID。
     */
    private Integer createdBy;

    /**
     * 签发医生的用户 ID。
     */
    private Integer signedBy;

    /**
     * 签发医生姓名快照。
     */
    private String signerNameSnapshot;

    /**
     * 签发医生执业/工号快照。
     */
    private String professionalNoSnapshot;

    private LocalDateTime signedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
