package com.example.retinavision.service.analysis.api;

import com.example.retinavision.mq.analysis.AnalysisTaskMessage;

public interface AnalysisTaskExecutionService {
    enum ExecutionDisposition {
        SUCCESS,
        FAILED,
        IGNORED,
        REQUEUE
    }

    ExecutionDisposition process(AnalysisTaskMessage message);
}

