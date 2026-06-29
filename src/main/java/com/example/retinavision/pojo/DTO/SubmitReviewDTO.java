package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 医生提交审核结论的请求 DTO。
 *
 * <p>医生审核是医疗闭环里的“最终人工确认”步骤。
 * 它可以直接审核 AI 原始结果，也可以选择一个已经提交的人工修正版本。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitReviewDTO {

    /**
     * 本次审核采用的修正版本。
     * 为空表示采用 AI 原始结果；不为空时后端会检查该修正版本是否真实存在。
     */
    private Integer correctionVersion;

    /**
     * 审核状态：待修改、通过、拒绝等。
     */
    private ReviewStatus status;

    /**
     * 医生所见：对图像和分割结果的专业描述。
     */
    private String findings;

    /**
     * 医生结论：是否认可结果、主要判断是什么。
     */
    private String conclusion;

    /**
     * 医生建议：后续处理、复查或临床建议。
     */
    private String recommendation;

    /**
     * 前端认为当前审核记录的版本号。
     * 用于乐观锁：防止两个医生同时保存时，后提交的人覆盖先提交的人。
     */
    private Integer expectedVersion;
}
