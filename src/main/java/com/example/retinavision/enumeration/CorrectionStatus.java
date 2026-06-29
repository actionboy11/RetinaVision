package com.example.retinavision.enumeration;

/**
 * 人工修正版本状态。
 */
public enum CorrectionStatus {
    /**
     * 草稿态，预留给未来“先保存后提交”的修正流程。
     */
    DRAFT,

    /**
     * 已提交，等待医生审核是否采纳。
     */
    SUBMITTED,

    /**
     * 医生审核通过，该修正版本可被报告采用。
     */
    ACCEPTED,

    /**
     * 医生审核拒绝，该修正版本不能用于正式报告。
     */
    REJECTED
}
