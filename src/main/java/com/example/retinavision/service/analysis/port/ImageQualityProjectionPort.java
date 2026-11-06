package com.example.retinavision.service.analysis.port;

public interface ImageQualityProjectionPort {

    void markErrorIfCurrent(long imageFileId, long taskId);
}
