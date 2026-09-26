package com.example.retinavision.agent;

import java.util.List;

public record AgentQueryContextSnapshot(AgentSkillCode currentSkill,
                                        SegmentationState segmentationState,
                                        int page,
                                        int pageSize,
                                        long total,
                                        Integer selectedCaseId,
                                        Long selectedTaskId,
                                        List<Integer> recentCaseIds) {
}
