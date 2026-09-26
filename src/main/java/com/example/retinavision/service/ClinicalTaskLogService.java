package com.example.retinavision.service;

public interface ClinicalTaskLogService {
    void appendResultEvent(Long resultId, String message, String operatorType, Integer operatorId);
}
