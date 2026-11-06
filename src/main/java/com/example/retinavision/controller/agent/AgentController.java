package com.example.retinavision.controller.agent;

import com.example.retinavision.pojo.agent.dto.AgentChatRequestDTO;
import com.example.retinavision.pojo.agent.entity.AgentChatMessageEntity;
import com.example.retinavision.pojo.agent.entity.AgentChatSessionEntity;
import com.example.retinavision.pojo.agent.vo.AgentChatMessageVO;
import com.example.retinavision.pojo.agent.vo.AgentChatResponseVO;
import com.example.retinavision.pojo.agent.vo.AgentChatSessionVO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.agent.AgentChatService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class AgentController {
    private final AgentChatService service;

    public AgentController(AgentChatService service) {
        this.service = service;
    }

    @PostMapping("/sessions")
    public Result<AgentChatSessionVO> createSession(Authentication authentication) {
        return Result.success(toSession(service.createSession(user(authentication))));
    }

    @GetMapping("/sessions")
    public Result<List<AgentChatSessionVO>> sessions(Authentication authentication) {
        return Result.success(service.listSessions(user(authentication)).stream().map(this::toSession).toList());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<AgentChatMessageVO>> messages(@PathVariable Long sessionId, Authentication authentication) {
        return Result.success(service.listMessages(sessionId, user(authentication)).stream().map(this::toMessage).toList());
    }

    @PostMapping("/chat")
    public Result<AgentChatResponseVO> chat(@RequestBody AgentChatRequestDTO request,
                                            Authentication authentication) {
        return Result.success(service.chat(request, user(authentication)));
    }

    private CurrentUserVO user(Authentication authentication) {
        return (CurrentUserVO) authentication.getPrincipal();
    }

    private AgentChatSessionVO toSession(AgentChatSessionEntity entity) {
        return new AgentChatSessionVO(entity.getId(), entity.getTitle(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private AgentChatMessageVO toMessage(AgentChatMessageEntity entity) {
        return new AgentChatMessageVO(entity.getId(), entity.getSessionId(), entity.getRole(),
                entity.getContent(), entity.getStructuredContentJson(), entity.getCreatedAt());
    }
}
