package com.example.retinavision.service;

import com.example.retinavision.mq.AnalysisTaskMessage;

public interface AnalysisTaskExecutionService {
    enum ExecutionDisposition {
        SUCCESS,
        FAILED,
        IGNORED,
        REQUEUE
    }

    ExecutionDisposition process(AnalysisTaskMessage message);
}

