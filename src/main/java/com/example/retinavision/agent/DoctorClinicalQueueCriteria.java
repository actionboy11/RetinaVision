package com.example.retinavision.agent;

public record DoctorClinicalQueueCriteria(DoctorClinicalQueueType queueType,
                                          DoctorDateWindow dateWindow) {
    public DoctorClinicalQueueCriteria {
        if (queueType == null) {
            throw new IllegalArgumentException("queueType is required");
        }
        if (dateWindow == null) dateWindow = DoctorDateWindow.ANY;
    }
}
