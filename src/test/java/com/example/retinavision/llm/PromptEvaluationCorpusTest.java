package com.example.retinavision.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PromptEvaluationCorpusTest {
    @Test
    void reportDraftCorpusContainsOnlyFixedStructuredSyntheticExamples() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/prompt-evaluation/report-draft-v1.json")) {
            assertThat(input).isNotNull();
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode corpus = new ObjectMapper().readTree(text);
            assertThat(corpus.path("version").asText()).isEqualTo(PromptEvaluationService.SAMPLE_VERSION);
            assertThat(corpus.path("cases").size()).isEqualTo(3);
            for (JsonNode testCase : corpus.path("cases")) {
                assertThat(testCase.path("context").path("result").isObject()).isTrue();
                assertThat(testCase.path("context").path("image").isObject()).isTrue();
            }
            assertThat(text).doesNotContain("patientName", "realName", "maskUrl", "apiKey", "Bearer ",
                    "C:\\\\", "/Users/", "sk-");
        }
    }
}
