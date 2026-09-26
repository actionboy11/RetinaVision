package com.example.retinavision.agent;

import java.util.Map;

public record AgentStructuredData(String type, Map<String, Object> payload) {
}
