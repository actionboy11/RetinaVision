package com.example.retinavision.agent;

import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.EyeSide;

public record DoctorCaseSearchCriteria(SegmentationState segmentationState,
                                       CaseWorkflowStatus workflowStatus,
                                       DoctorClinicalState clinicalState,
                                       DoctorDateWindow dateWindow,
                                       EyeSide eyeSide) {
    public DoctorCaseSearchCriteria {
        if (segmentationState == null) segmentationState = SegmentationState.ANY;
        if (clinicalState == null) clinicalState = DoctorClinicalState.ANY;
        if (dateWindow == null) dateWindow = DoctorDateWindow.ANY;
    }

    public DoctorCaseSearchCriteria(SegmentationState segmentationState, CaseWorkflowStatus workflowStatus) {
        this(segmentationState, workflowStatus, DoctorClinicalState.ANY, DoctorDateWindow.ANY, null);
    }
}
