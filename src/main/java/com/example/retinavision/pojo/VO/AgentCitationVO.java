package com.example.retinavision.pojo.VO;

public record AgentCitationVO(Long documentId, Long chunkId, String documentTitle,
                              String source, String snippet, double score) {
}
