package com.example.retinavision.agent;

import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.enumeration.EyeSide;

import java.util.List;

public record AgentQueryContextSnapshot(AgentSkillCode currentSkill,
                                        AgentReferenceType referenceType,
                                        SegmentationState segmentationState,
                                        DoctorClinicalState clinicalState,
                                        AnalysisTaskType taskType,
                                        DoctorTaskStatusFilter taskStatus,
                                        DoctorClinicalQueueType queueType,
                                        DoctorDateWindow dateWindow,
                                        EyeSide eyeSide,
                                        int page,
                                        int pageSize,
                                        long total,
                                        Integer selectedCaseId,
                                        Long selectedTaskId,
                                        List<Long> recentReferenceIds) {
    public AgentQueryContextSnapshot {
        if (referenceType == null) referenceType = AgentReferenceType.CASE;
        if (segmentationState == null) segmentationState = SegmentationState.ANY;
        if (clinicalState == null) clinicalState = DoctorClinicalState.ANY;
        if (taskStatus == null) taskStatus = DoctorTaskStatusFilter.ANY;
        if (dateWindow == null) dateWindow = DoctorDateWindow.ANY;
        if (recentReferenceIds == null) recentReferenceIds = List.of();
        recentReferenceIds = List.copyOf(recentReferenceIds.stream().limit(10).toList());
    }

    public AgentQueryContextSnapshot(AgentSkillCode currentSkill, AgentReferenceType referenceType,
                                     SegmentationState segmentationState, DoctorClinicalState clinicalState,
                                     DoctorDateWindow dateWindow, EyeSide eyeSide,
                                     int page, int pageSize, long total, Integer selectedCaseId,
                                     Long selectedTaskId, List<Integer> recentCaseIds) {
        this(currentSkill, referenceType, segmentationState, clinicalState,
                AnalysisTaskType.VESSEL_SEGMENTATION, DoctorTaskStatusFilter.ANY, null,
                dateWindow, eyeSide, page, pageSize, total, selectedCaseId, selectedTaskId,
                toLongs(recentCaseIds));
    }

    public AgentQueryContextSnapshot(AgentSkillCode currentSkill, SegmentationState segmentationState,
                                     DoctorClinicalState clinicalState, DoctorDateWindow dateWindow,
                                     EyeSide eyeSide, int page, int pageSize, long total,
                                     Integer selectedCaseId, Long selectedTaskId,
                                     List<Integer> recentCaseIds) {
        this(currentSkill, AgentReferenceType.CASE, segmentationState, clinicalState, dateWindow, eyeSide,
                page, pageSize, total, selectedCaseId, selectedTaskId, recentCaseIds);
    }

    public AgentQueryContextSnapshot(AgentSkillCode currentSkill, SegmentationState segmentationState,
                                     int page, int pageSize, long total, Integer selectedCaseId,
                                     Long selectedTaskId, List<Integer> recentCaseIds) {
        this(currentSkill, AgentReferenceType.CASE, segmentationState, DoctorClinicalState.ANY,
                DoctorDateWindow.ANY, null, page, pageSize, total,
                selectedCaseId, selectedTaskId, recentCaseIds);
    }

    private static List<Long> toLongs(List<Integer> values) {
        if (values == null) return List.of();
        return values.stream().map(Integer::longValue).toList();
    }
}
