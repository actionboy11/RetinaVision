package com.example.retinavision.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportDraftEvaluationScorer {
    private static final List<String> FIELDS = List.of("findings", "conclusion", "recommendation", "explanation");
    private final ObjectMapper json;
    private final LlmSafetyPolicy safetyPolicy;

    public ReportDraftEvaluationScorer(ObjectMapper json, LlmSafetyPolicy safetyPolicy) {
        this.json = json;
        this.safetyPolicy = safetyPolicy;
    }

    public Score score(String content) {
        try {
            JsonNode node = json.readTree(content);
            if (node == null || !node.isObject()) {
                return new Score(false, "", "INVALID_JSON");
            }
            for (String field : FIELDS) {
                JsonNode value = node.path(field);
                if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 1000) {
                    return new Score(false, "", "INVALID_FIELD");
                }
            }
            String disclaimer = safetyPolicy.disclaimer(PromptScenario.REPORT_DRAFT_GENERATION);
            if (!disclaimer.equals(node.path("disclaimer").asText())) {
                return new Score(false, "", "INVALID_DISCLAIMER");
            }
            safetyPolicy.requireSafe(PromptScenario.REPORT_DRAFT_GENERATION,
                    FIELDS.stream().map(field -> node.path(field).asText()).toArray(String[]::new));
            String normalized = json.writeValueAsString(node);
            return new Score(true, normalized.length() > 5000 ? normalized.substring(0, 5000) : normalized, null);
        } catch (LlmException exception) {
            return new Score(false, "", "UNSAFE_WORDING");
        } catch (Exception exception) {
            return new Score(false, "", "INVALID_JSON");
        }
    }

    public record Score(boolean passed, String output, String errorCode) {}
}
