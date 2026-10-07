package com.example.retinavision.agent.evaluation;

import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.pojo.VO.AnalysisResultComparisonVO;
import com.example.retinavision.pojo.VO.CaseAnalysisTimelineVO;
import com.example.retinavision.pojo.VO.CaseTrendSummaryVO;
import com.example.retinavision.service.CaseAnalysisTimelineService;

import java.time.LocalDateTime;
import java.util.List;

public class FixtureCaseAnalysisTimelineService implements CaseAnalysisTimelineService {
    @Override
    public CaseAnalysisTimelineVO timeline(Long caseId, EyeSide eyeSide, TaskType taskType,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        return new CaseAnalysisTimelineVO(caseId, eyeSide, List.of(
                item(31_001L, 51_001L, LocalDateTime.of(2026, 9, 1, 10, 0)),
                item(31_002L, 51_002L, LocalDateTime.of(2026, 10, 1, 10, 0))));
    }

    @Override
    public AnalysisResultComparisonVO compare(Long baselineResultId, Long targetResultId) {
        return new AnalysisResultComparisonVO(10_001L, EyeSide.LEFT, TaskType.VESSEL_SEGMENTATION,
                null, null, 2.0, 0.01, false, false, false,
                List.of("匿名评测结果仅用于验证结构"));
    }

    @Override
    public CaseTrendSummaryVO generateTrendSummary(Long caseId, EyeSide eyeSide, TaskType taskType) {
        throw new UnsupportedOperationException("评测 Fixture 不生成模型趋势摘要");
    }

    private CaseAnalysisTimelineVO.Item item(long taskId, long resultId, LocalDateTime time) {
        return new CaseAnalysisTimelineVO.Item(taskId, "EVAL-TASK-" + taskId,
                TaskType.VESSEL_SEGMENTATION, TaskStatus.SUCCESS, 1L, resultId,
                80.0, null, null, null, null, 0.08, "eval-model", "v1", 120,
                null, null, 1, time, time, time);
    }
}
