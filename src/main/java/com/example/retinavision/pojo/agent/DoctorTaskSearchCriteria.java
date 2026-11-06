package com.example.retinavision.pojo.agent;
import com.example.retinavision.enumeration.analysis.TaskType;

import com.example.retinavision.pojo.analysis.domain.AnalysisTaskType;

public record DoctorTaskSearchCriteria(AnalysisTaskType taskType,
                                       DoctorTaskStatusFilter status,
                                       DoctorDateWindow dateWindow) {
    public DoctorTaskSearchCriteria {
        if (taskType == null) taskType = AnalysisTaskType.VESSEL_SEGMENTATION;
        if (status == null) status = DoctorTaskStatusFilter.ANY;
        if (dateWindow == null) dateWindow = DoctorDateWindow.ANY;
    }
}
