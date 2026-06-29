package com.example.retinavision.enumeration;

/**
 * 报告生命周期状态。
 */
public enum ReportStatus {
    /**
     * 草稿：医生可以编辑，但普通用户不能下载。
     */
    DRAFT,

    /**
     * 已签发：可以下载正式 PDF。
     */
    SIGNED,

    /**
     * 已被新签发版本替代：仍可审计和下载，但不是当前最新版。
     */
    SUPERSEDED
}
