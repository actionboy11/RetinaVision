package com.example.retinavision.rag;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagGroundingValidatorTest {
    private final RagGroundingValidator validator =
            new RagGroundingValidator(new ObjectMapper(), new LlmSafetyPolicy());
    private final List<QdrantSearchHit> hits = List.of(
            new QdrantSearchHit("p1", 0.91, 1L, 11L, "指标说明", "院内资料",
                    "血管面积比例是分割结果的辅助性结构化指标，不可单独作为诊断依据。"),
            new QdrantSearchHit("p2", 0.78, 2L, 22L, "报告规则", "系统文档",
                    "正式 PDF 仅使用医生审核并保存的意见，不直接采用 AI 草稿。")
    );

    @Test
    void returnsOnlyCitationsWhoseQuoteExistsInRetrievedChunk() {
        var result = validator.validate("{\"answer\":\"血管面积比例是辅助指标。\","
                + "\"evidence\":[{\"chunkId\":11,\"quote\":\"血管面积比例是分割结果的辅助性结构化指标\"}]}", hits);

        assertThat(result.valid()).isTrue();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).chunkId()).isEqualTo(11L);
        assertThat(result.citations().get(0).snippet()).contains("血管面积比例");
    }

    @Test
    void rejectsMissingOrFabricatedEvidenceWithoutExposingAnswer() {
        assertThat(validator.validate("{\"answer\":\"看似合理\"}", hits).valid()).isFalse();
        var fabricated = validator.validate("{\"answer\":\"看似合理\","
                + "\"evidence\":[{\"chunkId\":99,\"quote\":\"并不存在的片段原文\"}]}", hits);
        assertThat(fabricated.valid()).isFalse();
        assertThat(fabricated.answer()).isEmpty();
        assertThat(validator.validate("{\"answer\":\"看似合理\","
                + "\"evidence\":[{\"chunkId\":11,\"quote\":\"原文没有的这段话\"}]}", hits).valid()).isFalse();
    }

    @Test
    void unsafeMedicalClaimsAreRejectedEvenWithRealQuote() {
        assertThatThrownBy(() -> validator.validate("{\"answer\":\"可以确诊某疾病\","
                + "\"evidence\":[{\"chunkId\":11,\"quote\":\"血管面积比例是分割结果的辅助性结构化指标\"}]}", hits))
                .isInstanceOf(BaseException.class).hasMessageContaining("不合规诊断措辞");
    }
}
