package com.example.retinavision.service.reporting.api;

import com.example.retinavision.pojo.reporting.entity.AnalysisReportEntity;

public interface AiReportDraftService {
    AnalysisReportEntity generateDraft(Long resultId, Integer userId);
}
