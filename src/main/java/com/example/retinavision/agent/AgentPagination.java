package com.example.retinavision.agent;

public record AgentPagination(int page, int pageSize, long total, boolean hasPrevious, boolean hasNext) {
}
