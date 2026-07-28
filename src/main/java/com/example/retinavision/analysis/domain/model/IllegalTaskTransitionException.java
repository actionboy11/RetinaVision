package com.example.retinavision.analysis.domain.model;

public final class IllegalTaskTransitionException extends IllegalStateException {

    public IllegalTaskTransitionException(AnalysisTaskStatus from, AnalysisTaskStatus to) {
        super("任务不能从 " + from + " 转换为 " + to);
    }
}
