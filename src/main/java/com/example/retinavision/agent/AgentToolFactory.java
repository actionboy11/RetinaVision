package com.example.retinavision.agent;

import com.example.retinavision.pojo.VO.CurrentUserVO;

import java.util.Arrays;
import java.util.Set;

public interface AgentToolFactory {
    AgentToolBundle create(Long sessionId, CurrentUserVO user, String traceId);

    default AgentToolBundle create(Long sessionId, CurrentUserVO user, String traceId,
                                   Set<String> allowedToolNames) {
        AgentToolBundle bundle = create(sessionId, user, traceId);
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            bundle.setCallbacks(new org.springframework.ai.tool.ToolCallback[0]);
            return bundle;
        }
        bundle.setCallbacks(Arrays.stream(bundle.callbacks())
                .filter(callback -> allowedToolNames.contains(callback.getToolDefinition().name()))
                .toArray(org.springframework.ai.tool.ToolCallback[]::new));
        return bundle;
    }
}
