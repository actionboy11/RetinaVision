package com.example.retinavision.pojo.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新报告草稿的请求 DTO。
 *
 * <p>报告草稿只能由医生维护；正式签发后不允许再修改同一个版本。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReportDraftDTO {

    /**
     * 报告采用的人工修正版本。
     * 为空表示报告基于 AI 原始结果；非空时必须是已被医生审核接受的修正版本。
     */
    private Integer correctionVersion;

    /**
     * 报告草稿的结构化 JSON。
     * 里面通常保存 findings、conclusion、recommendation、disclaimer 等字段。
     */
    private String draftJson;
}
