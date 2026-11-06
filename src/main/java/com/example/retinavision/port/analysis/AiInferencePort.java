package com.example.retinavision.port.analysis;

import com.example.retinavision.pojo.analysis.dto.InferenceOutput;
import com.example.retinavision.pojo.analysis.dto.SourceImage;

public interface AiInferencePort {

    InferenceOutput checkQuality(SourceImage image, String traceId);

    InferenceOutput segment(SourceImage image, String traceId);

    byte[] downloadMask(String artifactUrl, String traceId);
}
