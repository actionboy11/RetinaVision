package com.example.retinavision.pojo.agent;

public record AgentPagination(int page, int pageSize, long total, boolean hasPrevious, boolean hasNext) {
}
