package com.example.retinavision.agent;

public record AgentAction(String type, String label, Long targetId, String targetPath) {
}
