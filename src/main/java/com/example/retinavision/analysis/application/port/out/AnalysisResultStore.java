package com.example.retinavision.analysis.application.port.out;

import com.example.retinavision.analysis.application.model.InferenceOutput;

public interface AnalysisResultStore {

    long saveQuality(long taskId, long imageFileId, InferenceOutput output);

    long saveSegmentation(long taskId, InferenceOutput output, String maskObjectKey);
}
