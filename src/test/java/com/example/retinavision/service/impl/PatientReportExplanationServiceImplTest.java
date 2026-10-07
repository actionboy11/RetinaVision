package com.example.retinavision.service.impl;

import com.example.retinavision.agent.PatientReportExplanationResult;
import com.example.retinavision.llm.LlmException;
import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PatientReportExplanationServiceImplTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void sendsOnlySignedReportAllowlistAndReturnsSafeExplanation() throws Exception {
        LlmOrchestrationService llm = (templateCode, context) -> {
            assertThat(templateCode).isEqualTo("PATIENT_SIGNED_REPORT_EXPLANATION");
            JsonNode root;
            try {
                root = json.readTree(context);
            } catch (Exception exception) {
                throw new AssertionError("sanitized context must be valid JSON", exception);
            }
            Set<String> fields = new HashSet<>();
            root.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder(
                    "caseId", "caseNo", "resultId", "version", "signedAt", "signerName",
                    "findings", "conclusion", "recommendation");
            assertThat(context).doesNotContain("mask", "model", "path", "draft", "token");
            return generated("{\"explanation\":\"医生报告提示本次检查需要按建议继续复查。\"}");
        };

        PatientReportExplanationResult result = service(llm).explain(report());

        assertThat(result.available()).isTrue();
        assertThat(result.explanation()).contains("医生报告");
        assertThat(result.message()).isNull();
    }

    @Test
    void blankOrUnsafeOutputDegradesWithoutChangingSourceReport() {
        PatientAgentReportDetailVO report = report();
        String sourceConclusion = report.getConclusion();

        PatientReportExplanationResult blank = service((code, context) -> generated("{\"explanation\":\"\"}"))
                .explain(report);
        PatientReportExplanationResult unsafe = service((code, context) ->
                generated("{\"explanation\":\"可以确诊，无需复查。\"}"))
                .explain(report);

        assertUnavailable(blank);
        assertUnavailable(unsafe);
        assertThat(report.getConclusion()).isEqualTo(sourceConclusion);
    }

    @Test
    void modelTimeoutAndProtocolFailureReturnFixedUnavailableResult() {
        PatientReportExplanationResult timeout = service((code, context) -> {
            throw new LlmException("upstream timeout at /srv/private/model");
        }).explain(report());
        PatientReportExplanationResult protocol = service((code, context) -> generated("not-json"))
                .explain(report());

        assertUnavailable(timeout);
        assertUnavailable(protocol);
        assertThat(timeout.message()).doesNotContain("timeout", "/srv", "private");
    }

    private void assertUnavailable(PatientReportExplanationResult result) {
        assertThat(result.available()).isFalse();
        assertThat(result.explanation()).isNull();
        assertThat(result.message()).isEqualTo("报告通俗解释暂不可用，您仍可查看医生签发的正式报告");
    }

    private PatientReportExplanationServiceImpl service(LlmOrchestrationService llm) {
        return new PatientReportExplanationServiceImpl(llm, json, new LlmSafetyPolicy());
    }

    private PatientAgentReportDetailVO report() {
        PatientAgentReportDetailVO report = new PatientAgentReportDetailVO();
        report.setCaseId(10L);
        report.setCaseNo("C-20261007-001");
        report.setResultId(91L);
        report.setVersion(2);
        report.setSignedAt(LocalDateTime.of(2026, 10, 7, 8, 30));
        report.setSignerName("演示医生");
        report.setFindings("医生所见");
        report.setConclusion("医生结论");
        report.setRecommendation("建议按期复查");
        return report;
    }

    private LlmGenerationResult generated(String content) {
        return new LlmGenerationResult(content, "PATIENT_SIGNED_REPORT_EXPLANATION",
                1, "qwen", "qwen-plus", 15);
    }
}
