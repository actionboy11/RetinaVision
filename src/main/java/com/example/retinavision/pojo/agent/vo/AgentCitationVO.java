package com.example.retinavision.pojo.agent.vo;

public record AgentCitationVO(Long documentId, Long chunkId, String documentTitle,
                              String source, String snippet, double score) {
}
