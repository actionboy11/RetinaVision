package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.AgentChatRequestDTO;
import com.example.retinavision.pojo.Entity.AgentChatMessageEntity;
import com.example.retinavision.pojo.Entity.AgentChatSessionEntity;
import com.example.retinavision.pojo.VO.AgentChatResponseVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;

import java.util.List;

public interface AgentChatService {
    AgentChatSessionEntity createSession(CurrentUserVO user);

    List<AgentChatSessionEntity> listSessions(CurrentUserVO user);

    List<AgentChatMessageEntity> listMessages(Long sessionId, CurrentUserVO user);

    AgentChatResponseVO chat(AgentChatRequestDTO request, CurrentUserVO user);
}
