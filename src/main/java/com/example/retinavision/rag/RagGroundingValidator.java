package com.example.retinavision.rag;

import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.llm.PromptScenario;
import com.example.retinavision.pojo.VO.KnowledgeChatResponseVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RagGroundingValidator {
    private final ObjectMapper json;
    private final LlmSafetyPolicy safetyPolicy;

    public RagGroundingValidator(ObjectMapper json, LlmSafetyPolicy safetyPolicy) {
        this.json = json;
        this.safetyPolicy = safetyPolicy;
    }

    public Validation validate(String content, List<QdrantSearchHit> hits) {
        try {
            JsonNode root = json.readTree(content);
            if (root == null || !root.isObject() || !root.path("answer").isTextual()) {
                return Validation.invalid();
            }
            String answer = root.path("answer").asText().trim();
            if (answer.isEmpty() || answer.length() > 1500) {
                return Validation.invalid();
            }
            safetyPolicy.requireSafe(PromptScenario.RAG_KNOWLEDGE_CHAT, answer);
            JsonNode evidence = root.path("evidence");
            if (!evidence.isArray() || evidence.isEmpty() || evidence.size() > 5) {
                return Validation.invalid();
            }
            Map<Long, QdrantSearchHit> byChunk = hits.stream().collect(Collectors.toMap(
                    QdrantSearchHit::chunkId, Function.identity(), (first, second) -> first));
            List<KnowledgeChatResponseVO.Citation> citations = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (JsonNode item : evidence) {
                JsonNode idNode = item.path("chunkId");
                JsonNode quoteNode = item.path("quote");
                if (!idNode.canConvertToLong() || !quoteNode.isTextual()) {
                    return Validation.invalid();
                }
                long chunkId = idNode.asLong();
                String quote = quoteNode.asText().trim();
                QdrantSearchHit hit = byChunk.get(chunkId);
                if (hit == null || !seen.add(chunkId) || quote.length() < 8 || quote.length() > 220
                        || hit.text() == null || !hit.text().contains(quote)) {
                    return Validation.invalid();
                }
                citations.add(new KnowledgeChatResponseVO.Citation(hit.documentId(), hit.documentTitle(),
                        hit.chunkId(), hit.source(), quote, hit.score()));
            }
            return new Validation(true, answer, List.copyOf(citations));
        } catch (com.example.retinavision.exception.BaseException exception) {
            throw exception;
        } catch (Exception exception) {
            return Validation.invalid();
        }
    }

    public record Validation(boolean valid, String answer, List<KnowledgeChatResponseVO.Citation> citations) {
        public static Validation invalid() {
            return new Validation(false, "", List.of());
        }
    }
}
