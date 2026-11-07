package com.example.retinavision.port.analysis;

import com.example.retinavision.pojo.analysis.dto.InferenceOutput;

public interface AnalysisResultStore {

    long saveQuality(long taskId, long imageFileId, InferenceOutput output);

    long saveSegmentation(long taskId, InferenceOutput output, String maskObjectKey);
}
