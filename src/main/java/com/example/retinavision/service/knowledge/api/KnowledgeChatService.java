package com.example.retinavision.service.knowledge.api;

import com.example.retinavision.pojo.knowledge.dto.KnowledgeChatRequestDTO;
import com.example.retinavision.pojo.knowledge.entity.KnowledgeChatMessageEntity;
import com.example.retinavision.pojo.knowledge.entity.KnowledgeChatSessionEntity;
import com.example.retinavision.pojo.knowledge.vo.KnowledgeChatResponseVO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;

import java.util.List;

public interface KnowledgeChatService {
    KnowledgeChatSessionEntity createSession(Integer userId);
    List<KnowledgeChatSessionEntity> listSessions(Integer userId);
    List<KnowledgeChatMessageEntity> listMessages(Long sessionId, Integer userId);
    KnowledgeChatResponseVO chat(KnowledgeChatRequestDTO request, Integer userId);
    KnowledgeChatResponseVO chat(KnowledgeChatRequestDTO request, CurrentUserVO user);
}
