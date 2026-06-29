package com.example.retinavision.enumeration;

/**
 * 医生审核状态。
 */
public enum ReviewStatus {
    /**
     * 待审核。
     */
    PENDING,

    /**
     * 医生认为还需要修改或补充。
     */
    CHANGES_REQUESTED,

    /**
     * 医生审核通过，允许进入报告签发流程。
     */
    APPROVED,

    /**
     * 医生拒绝该结果，不能签发正式报告。
     */
    REJECTED
}
