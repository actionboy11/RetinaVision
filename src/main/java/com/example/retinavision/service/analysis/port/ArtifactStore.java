package com.example.retinavision.service.analysis.port;

public interface ArtifactStore {

    String storeMask(long taskId, byte[] bytes);
}
