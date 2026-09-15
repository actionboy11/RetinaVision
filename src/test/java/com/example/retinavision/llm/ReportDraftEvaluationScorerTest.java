package com.example.retinavision.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDraftEvaluationScorerTest {
    private final ReportDraftEvaluationScorer scorer =
            new ReportDraftEvaluationScorer(new ObjectMapper(), new LlmSafetyPolicy());

    @Test
    void acceptsCompleteSafeDraftWithFixedDisclaimer() {
        var result = scorer.score("{\"findings\":\"可见分割血管区域\","
                + "\"conclusion\":\"辅助分析结果需结合临床复核\","
                + "\"recommendation\":\"建议医生复核原图\","
                + "\"explanation\":\"面积比例为模型输出指标\","
                + "\"disclaimer\":\"AI辅助分析，不等同于独立医学诊断。\"}");
        assertThat(result.passed()).isTrue();
        assertThat(result.output()).contains("可见分割血管区域");
    }

    @Test
    void rejectsUnsafeWordingAndDoesNotExposeItAsPassed() {
        var result = scorer.score("{\"findings\":\"正常\",\"conclusion\":\"确诊糖尿病视网膜病变\","
                + "\"recommendation\":\"复核\",\"explanation\":\"解释\","
                + "\"disclaimer\":\"AI辅助分析，不等同于独立医学诊断。\"}");
        assertThat(result.passed()).isFalse();
    }

    @Test
    void rejectsMissingFieldsAndAlteredDisclaimer() {
        assertThat(scorer.score("{\"findings\":\"a\"}").passed()).isFalse();
        assertThat(scorer.score("{\"findings\":\"a\",\"conclusion\":\"b\","
                + "\"recommendation\":\"c\",\"explanation\":\"d\","
                + "\"disclaimer\":\"可作为正式诊断\"}").passed()).isFalse();
    }
}
