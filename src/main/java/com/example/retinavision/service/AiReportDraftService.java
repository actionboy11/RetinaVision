package com.example.retinavision.service;

import com.example.retinavision.pojo.Entity.AnalysisReportEntity;

public interface AiReportDraftService {
    AnalysisReportEntity generateDraft(Long resultId, Integer userId);
}
