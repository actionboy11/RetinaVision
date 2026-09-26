package com.example.retinavision.agent;

import com.example.retinavision.enumeration.CaseWorkflowStatus;

public record DoctorCaseSearchCriteria(SegmentationState segmentationState,
                                       CaseWorkflowStatus workflowStatus) {
    public DoctorCaseSearchCriteria {
        if (segmentationState == null) segmentationState = SegmentationState.ANY;
    }
}
