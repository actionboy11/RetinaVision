package com.example.retinavision.enumeration;

/**
 * 人工反馈对 AI 结果的总体判断。
 */
public enum FeedbackVerdict {
    /**
     * 认为 AI 结果基本可接受。
     */
    ACCEPTED,

    /**
     * 认为 AI 结果部分可用，但存在局部问题。
     */
    PARTIAL,

    /**
     * 认为 AI 结果整体不正确。
     */
    INCORRECT
}
