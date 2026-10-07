package com.example.retinavision.pojo.VO;

import java.util.List;
import java.util.Map;

public record AgentEvaluationOptionsVO(List<ModelOption> models,
                                       Map<String, List<VersionOption>> skillVersions,
                                       Map<String, List<VersionOption>> promptVersions) {
    public record ModelOption(String key, String provider, String model) {}
    public record VersionOption(Long id, Integer version, boolean active) {}
}
