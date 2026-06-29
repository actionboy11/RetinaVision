package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.CorrectionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * analysis_correction 表实体。
 *
 * <p>含义：人工修正版本。它不会覆盖 AI 原始 mask，而是为同一个 resultId
 * 追加 v1、v2、v3... 这样的独立版本。</p>
 */
@Data
@TableName("analysis_correction")
public class AnalysisCorrectionEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被修正的 AI 分析结果 ID。
     */
    private Long resultId;

    /**
     * 同一 resultId 下的修正版本号，从 1 开始递增。
     */
    private Integer version;

    /**
     * 修正后的结构化结果 JSON。
     * 如果只修正 mask，当前实现会保存 "{}"。
     */
    private String correctedResultJson;

    /**
     * 修正 mask 在本地结果目录下的对象 key。
     */
    private String correctedMaskObjectKey;

    /**
     * 上传修正的原因，便于审计和医生判断是否采纳。
     */
    private String reason;

    /**
     * 修正版本状态：提交、被接受、被拒绝等。
     */
    private CorrectionStatus status;

    /**
     * 上传修正版本的用户 ID。
     */
    private Integer submittedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
