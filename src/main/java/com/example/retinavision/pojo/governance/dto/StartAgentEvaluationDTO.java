package com.example.retinavision.pojo.governance.dto;

import java.util.Map;

public record StartAgentEvaluationDTO(Long datasetId,
                                      String targetRole,
                                      String modelKey,
                                      Map<String, Long> skillVersions,
                                      Map<String, Long> promptVersions) {
}
