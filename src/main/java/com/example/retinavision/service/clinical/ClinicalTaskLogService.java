package com.example.retinavision.service.clinical;

public interface ClinicalTaskLogService {
    void appendResultEvent(Long resultId, String message, String operatorType, Integer operatorId);
}
