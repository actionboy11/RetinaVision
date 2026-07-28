package com.example.retinavision.analysis.application.port.out;

import com.example.retinavision.analysis.application.model.InferenceOutput;
import com.example.retinavision.analysis.application.model.SourceImage;

public interface AiInferencePort {

    InferenceOutput checkQuality(SourceImage image, String traceId);

    InferenceOutput segment(SourceImage image, String traceId);

    byte[] downloadMask(String artifactUrl, String traceId);
}
