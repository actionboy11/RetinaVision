package com.example.retinavision.analysis.application.port.out;

public interface ArtifactStore {

    String storeMask(long taskId, byte[] bytes);
}
