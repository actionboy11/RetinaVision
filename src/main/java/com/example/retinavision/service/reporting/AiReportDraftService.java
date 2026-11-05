package com.example.retinavision.service.reporting;

import com.example.retinavision.pojo.Entity.AnalysisReportEntity;

public interface AiReportDraftService {
    AnalysisReportEntity generateDraft(Long resultId, Integer userId);
}
