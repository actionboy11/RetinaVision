package com.example.retinavision.agent;

import com.example.retinavision.analysis.domain.model.AnalysisTaskType;

public record DoctorTaskSearchCriteria(AnalysisTaskType taskType,
                                       DoctorTaskStatusFilter status,
                                       DoctorDateWindow dateWindow) {
    public DoctorTaskSearchCriteria {
        if (taskType == null) taskType = AnalysisTaskType.VESSEL_SEGMENTATION;
        if (status == null) status = DoctorTaskStatusFilter.ANY;
        if (dateWindow == null) dateWindow = DoctorDateWindow.ANY;
    }
}
