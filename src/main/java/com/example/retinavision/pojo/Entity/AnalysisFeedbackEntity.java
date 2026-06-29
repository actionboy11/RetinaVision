package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.FeedbackVerdict;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * analysis_feedback 表实体。
 *
 * <p>含义：用户、研究员或医生对某个 AI 分析结果追加反馈。
 * 反馈是 append-only 记录，不修改 analysis_result 原始 AI 结果。</p>
 */
@Data
@TableName("analysis_feedback")
public class AnalysisFeedbackEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的 AI 分析结果 ID，对应 analysis_result.id。
     */
    private Long resultId;

    /**
     * 人工总体判断。
     */
    private FeedbackVerdict verdict;

    /**
     * 问题代码 JSON 字符串。
     * 这里用 String 是为了保持数据库层简单，业务层负责 JSON 序列化和反序列化。
     */
    private String issueCodes;

    /**
     * 反馈说明。
     */
    private String comment;

    /**
     * 提交反馈的用户 ID。
     */
    private Integer submittedBy;

    /**
     * 提交时间。
     */
    private LocalDateTime createdAt;
}
