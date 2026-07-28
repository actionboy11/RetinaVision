package com.example.retinavision.analysis.application.port.out;

public interface ImageQualityProjectionPort {

    void markErrorIfCurrent(long imageFileId, long taskId);
}
