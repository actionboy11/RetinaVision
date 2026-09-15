package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.KnowledgeChatRequestDTO;
import com.example.retinavision.pojo.Entity.KnowledgeChatMessageEntity;
import com.example.retinavision.pojo.Entity.KnowledgeChatSessionEntity;
import com.example.retinavision.pojo.VO.KnowledgeChatResponseVO;

import java.util.List;

public interface KnowledgeChatService {
    KnowledgeChatSessionEntity createSession(Integer userId);
    List<KnowledgeChatSessionEntity> listSessions(Integer userId);
    List<KnowledgeChatMessageEntity> listMessages(Long sessionId, Integer userId);
    KnowledgeChatResponseVO chat(KnowledgeChatRequestDTO request, Integer userId);
}
