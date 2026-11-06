package com.example.retinavision.pojo.agent;

import java.util.Map;

public record AgentStructuredData(String type, Map<String, Object> payload) {
}
