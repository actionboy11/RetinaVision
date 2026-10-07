package com.example.retinavision.service.impl;

import com.example.retinavision.agent.PatientReportExplanationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.llm.PromptScenario;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;
import com.example.retinavision.service.PatientReportExplanationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PatientReportExplanationServiceImpl implements PatientReportExplanationService {
    private static final String TEMPLATE_CODE = "PATIENT_SIGNED_REPORT_EXPLANATION";
    private static final String UNAVAILABLE_MESSAGE =
            "报告通俗解释暂不可用，您仍可查看医生签发的正式报告";

    private final LlmOrchestrationService llm;
    private final ObjectMapper json;
    private final LlmSafetyPolicy safetyPolicy;

    public PatientReportExplanationServiceImpl(LlmOrchestrationService llm,
                                                ObjectMapper json,
                                                LlmSafetyPolicy safetyPolicy) {
        this.llm = llm;
        this.json = json;
        this.safetyPolicy = safetyPolicy;
    }

    @Override
    public PatientReportExplanationResult explain(PatientAgentReportDetailVO report) {
        if (report == null) return PatientReportExplanationResult.unavailable(UNAVAILABLE_MESSAGE);
        try {
            String context = json.writeValueAsString(allowlistedContext(report));
            JsonNode root = json.readTree(llm.generateJson(TEMPLATE_CODE, context).content());
            String explanation = safetyPolicy.requireText(root.path("explanation").asText(""),
                    1200, "患者报告解释缺少 explanation");
            safetyPolicy.requireSafe(PromptScenario.PATIENT_SIGNED_REPORT_EXPLANATION, explanation);
            return PatientReportExplanationResult.available(explanation);
        } catch (Exception ignored) {
            return PatientReportExplanationResult.unavailable(UNAVAILABLE_MESSAGE);
        }
    }

    private Map<String, Object> allowlistedContext(PatientAgentReportDetailVO report) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("caseId", report.getCaseId());
        context.put("caseNo", report.getCaseNo());
        context.put("resultId", report.getResultId());
        context.put("version", report.getVersion());
        context.put("signedAt", report.getSignedAt());
        context.put("signerName", report.getSignerName());
        context.put("findings", report.getFindings());
        context.put("conclusion", report.getConclusion());
        context.put("recommendation", report.getRecommendation());
        return context;
    }
}
