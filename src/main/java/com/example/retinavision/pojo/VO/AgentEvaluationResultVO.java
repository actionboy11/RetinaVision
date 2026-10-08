package com.example.retinavision.pojo.VO;

import java.util.Map;

public record AgentEvaluationResultVO(Long caseId, String category, String inputSummary,
                                      String expectedSkill, String actualSkill,
                                      Map<String, String> expectedArguments,
                                      Map<String, String> actualArguments,
                                      Boolean success, String errorType, String errorSummary,
                                      Long latencyMs) {
}
