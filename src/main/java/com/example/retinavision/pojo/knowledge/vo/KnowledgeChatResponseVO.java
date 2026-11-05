package com.example.retinavision.pojo.knowledge.vo;

import java.util.List;

public record KnowledgeChatResponseVO(
        Long sessionId,
        String answer,
        List<Citation> citations,
        String disclaimer
) {
    public record Citation(
            Long documentId,
            String documentTitle,
            Long chunkId,
            String source,
            String snippet,
            Double score
    ) {
    }
}
