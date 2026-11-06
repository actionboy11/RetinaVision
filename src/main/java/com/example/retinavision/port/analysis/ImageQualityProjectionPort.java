package com.example.retinavision.port.analysis;

public interface ImageQualityProjectionPort {

    void markErrorIfCurrent(long imageFileId, long taskId);
}
