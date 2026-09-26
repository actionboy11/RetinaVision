package com.example.retinavision.service;

import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.pojo.VO.AnalysisResultComparisonVO;
import com.example.retinavision.pojo.VO.CaseAnalysisTimelineVO;
import com.example.retinavision.pojo.VO.CaseTrendSummaryVO;

import java.time.LocalDateTime;

public interface CaseAnalysisTimelineService {
    CaseAnalysisTimelineVO timeline(Long caseId, EyeSide eyeSide, TaskType taskType, LocalDateTime startTime, LocalDateTime endTime);

    AnalysisResultComparisonVO compare(Long baselineResultId, Long targetResultId);

    CaseTrendSummaryVO generateTrendSummary(Long caseId, EyeSide eyeSide, TaskType taskType);
}
