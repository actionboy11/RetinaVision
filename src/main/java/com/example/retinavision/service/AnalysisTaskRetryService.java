package com.example.retinavision.service;

public interface AnalysisTaskRetryService {

    Integer prepareAutomaticRetry(Long taskId);

    void markRetryPublishFailed(Long taskId, String reason);
}
