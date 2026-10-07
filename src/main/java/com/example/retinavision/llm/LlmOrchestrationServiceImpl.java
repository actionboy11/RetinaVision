package com.example.retinavision.llm;

import com.example.retinavision.pojo.Entity.LlmCallLogEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.service.LlmCallLogService;
import com.example.retinavision.service.PromptTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LlmOrchestrationServiceImpl implements LlmOrchestrationService {
    private final PromptTemplateService templates;
    private final PromptRenderService renderer;
    private final LlmClient client;
    private final LlmCallLogService logs;
    private final LlmProperties properties;
    private final LlmSafetyPolicy safetyPolicy;
    private final ObjectMapper json;

    public LlmOrchestrationServiceImpl(PromptTemplateService templates,
                                       PromptRenderService renderer,
                                       LlmClient client,
                                       LlmCallLogService logs,
                                       LlmProperties properties,
                                       LlmSafetyPolicy safetyPolicy,
                                       ObjectMapper json) {
        this.templates = templates;
        this.renderer = renderer;
        this.client = client;
        this.logs = logs;
        this.properties = properties;
        this.safetyPolicy = safetyPolicy;
        this.json = json;
    }

    @Override
    public LlmGenerationResult generateJson(String templateCode, String sanitizedUserContext) {
        PromptTemplateVersionEntity version = templates.requireActiveVersion(templateCode);
        return generate(templateCode, version, sanitizedUserContext, "BUSINESS", null);
    }

    @Override
    public LlmGenerationResult generateJsonForEvaluation(String templateCode, Long versionId,
                                                         String sanitizedUserContext, Long evaluationRunId) {
        PromptTemplateVersionEntity version = templates.requireVersion(templateCode, versionId);
        return generate(templateCode, version, sanitizedUserContext, "EVALUATION", evaluationRunId);
    }

    private LlmGenerationResult generate(String templateCode, PromptTemplateVersionEntity version,
                                         String sanitizedUserContext, String callSource,
                                         Long evaluationRunId) {
        RenderedPrompt prompt = renderer.render(version, sanitizedUserContext);
        long startedAt = System.nanoTime();
        try {
            String content = client.generateJson(prompt.systemPrompt(), prompt.userPrompt());
            validateOutput(templateCode, version, content);
            long latencyMs = elapsedMillis(startedAt);
            logs.record(callLog(templateCode, version, true, latencyMs, null, callSource, evaluationRunId));
            return new LlmGenerationResult(content, templateCode, version.getVersion(),
                    properties.getProvider(), properties.getModel(), latencyMs);
        } catch (RuntimeException exception) {
            logs.record(callLog(templateCode, version, false, elapsedMillis(startedAt), safeErrorSummary(exception),
                    callSource, evaluationRunId));
            throw exception;
        }
    }

    private void validateOutput(String templateCode,
                                PromptTemplateVersionEntity version,
                                String content) {
        try {
            JsonNode output = json.readTree(content);
            if (output == null || !output.isObject()) {
                throw new LlmException("LLM response must be a valid JSON object");
            }
            JsonNode contract = version.getOutputContract() == null || version.getOutputContract().isBlank()
                    ? null : json.readTree(version.getOutputContract());
            if (contract != null && contract.path("required").isArray()) {
                for (JsonNode fieldNode : contract.path("required")) {
                    String field = fieldNode.asText();
                    JsonNode value = output.get(field);
                    if (value == null || value.isNull()
                            || (value.isTextual() && value.asText().isBlank())) {
                        throw new LlmException("LLM response is missing required field: " + field);
                    }
                    String expectedType = contract.path("properties").path(field).path("type").asText("");
                    if (!matchesType(value, expectedType)) {
                        throw new LlmException("LLM response field has invalid type: " + field);
                    }
                }
            }
            List<String> textValues = new java.util.ArrayList<>();
            output.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    textValues.add(entry.getValue().asText());
                }
            });
            safetyPolicy.requireSafe(PromptScenario.fromTemplateCode(templateCode), textValues.toArray(String[]::new));
        } catch (LlmException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LlmException("LLM response must be valid JSON", exception);
        }
    }

    private boolean matchesType(JsonNode value, String expectedType) {
        return switch (expectedType) {
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private LlmCallLogEntity callLog(String templateCode,
                                     PromptTemplateVersionEntity version,
                                     boolean success,
                                     long latencyMs,
                                     String errorSummary,
                                     String callSource,
                                     Long evaluationRunId) {
        LlmCallLogEntity log = new LlmCallLogEntity();
        log.setScenario(PromptScenario.fromTemplateCode(templateCode).name());
        log.setTemplateCode(templateCode);
        log.setTemplateVersion(version.getVersion());
        log.setProvider(properties.getProvider());
        log.setModel(properties.getModel());
        log.setSuccess(success);
        log.setLatencyMs(latencyMs);
        log.setErrorSummary(errorSummary);
        log.setCallSource(callSource);
        log.setEvaluationRunId(evaluationRunId);
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    private String safeErrorSummary(RuntimeException exception) {
        return exception.getClass().getSimpleName() + ": 大模型调用失败";
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
