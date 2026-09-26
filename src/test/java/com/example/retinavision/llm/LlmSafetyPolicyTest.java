package com.example.retinavision.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmSafetyPolicyTest {
    private final LlmSafetyPolicy policy = new LlmSafetyPolicy();

    @Test
    void keepsSystemOwnedDisclaimersPerScenario() {
        assertThat(policy.disclaimer(PromptScenario.REPORT_DRAFT_GENERATION))
                .isEqualTo("AI辅助分析，不等同于独立医学诊断。");
        assertThat(policy.disclaimer(PromptScenario.RAG_KNOWLEDGE_CHAT))
                .contains("不构成诊断、治疗建议或报告签发依据");
    }

    @Test
    void rejectsHighRiskMedicalWordingAcrossLlmScenarios() {
        assertThatThrownBy(() -> policy.requireSafe(
                PromptScenario.CASE_TREND_SUMMARY, "可以确诊该疾病，无需就医"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("不合规诊断措辞");
    }
}
