package com.example.retinavision.port.analysis;

public interface ArtifactStore {

    String storeMask(long taskId, byte[] bytes);
}
