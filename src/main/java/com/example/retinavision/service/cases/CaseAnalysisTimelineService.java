package com.example.retinavision.service.cases;

import com.example.retinavision.enumeration.cases.EyeSide;
import com.example.retinavision.enumeration.analysis.TaskType;
import com.example.retinavision.pojo.analysis.vo.AnalysisResultComparisonVO;
import com.example.retinavision.pojo.cases.vo.CaseAnalysisTimelineVO;
import com.example.retinavision.pojo.cases.vo.CaseTrendSummaryVO;

import java.time.LocalDateTime;

public interface CaseAnalysisTimelineService {
    CaseAnalysisTimelineVO timeline(Long caseId, EyeSide eyeSide, TaskType taskType, LocalDateTime startTime, LocalDateTime endTime);

    AnalysisResultComparisonVO compare(Long baselineResultId, Long targetResultId);

    CaseTrendSummaryVO generateTrendSummary(Long caseId, EyeSide eyeSide, TaskType taskType);
}
