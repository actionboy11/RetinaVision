package com.example.retinavision.service.agent.api;

import com.example.retinavision.pojo.agent.dto.AgentChatRequestDTO;
import com.example.retinavision.pojo.agent.entity.AgentChatMessageEntity;
import com.example.retinavision.pojo.agent.entity.AgentChatSessionEntity;
import com.example.retinavision.pojo.agent.vo.AgentChatResponseVO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;

import java.util.List;

public interface AgentChatService {
    AgentChatSessionEntity createSession(CurrentUserVO user);

    List<AgentChatSessionEntity> listSessions(CurrentUserVO user);

    List<AgentChatMessageEntity> listMessages(Long sessionId, CurrentUserVO user);

    AgentChatResponseVO chat(AgentChatRequestDTO request, CurrentUserVO user);
}
