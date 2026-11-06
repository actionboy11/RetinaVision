package com.example.retinavision.service.analysis.api;

public interface AnalysisTaskRetryService {

    Integer prepareAutomaticRetry(Long taskId);

    void markRetryPublishFailed(Long taskId, String reason);
}
