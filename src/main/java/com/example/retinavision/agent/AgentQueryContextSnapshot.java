package com.example.retinavision.agent;

import com.example.retinavision.enumeration.EyeSide;

import java.util.List;

public record AgentQueryContextSnapshot(AgentSkillCode currentSkill,
                                        SegmentationState segmentationState,
                                        DoctorClinicalState clinicalState,
                                        DoctorDateWindow dateWindow,
                                        EyeSide eyeSide,
                                        int page,
                                        int pageSize,
                                        long total,
                                        Integer selectedCaseId,
                                        Long selectedTaskId,
                                        List<Integer> recentCaseIds) {
    public AgentQueryContextSnapshot(AgentSkillCode currentSkill, SegmentationState segmentationState,
                                     int page, int pageSize, long total, Integer selectedCaseId,
                                     Long selectedTaskId, List<Integer> recentCaseIds) {
        this(currentSkill, segmentationState, DoctorClinicalState.ANY, DoctorDateWindow.ANY, null,
                page, pageSize, total, selectedCaseId, selectedTaskId, recentCaseIds);
    }
}
