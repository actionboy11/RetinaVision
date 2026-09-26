package com.example.retinavision.controller;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.DTO.KnowledgeChatRequestDTO;
import com.example.retinavision.pojo.DTO.KnowledgeDocumentCreateDTO;
import com.example.retinavision.pojo.DTO.KnowledgeDocumentStatusDTO;
import com.example.retinavision.pojo.Entity.KnowledgeChatMessageEntity;
import com.example.retinavision.pojo.Entity.KnowledgeChatSessionEntity;
import com.example.retinavision.pojo.Entity.KnowledgeDocumentEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.KnowledgeChatMessageVO;
import com.example.retinavision.pojo.VO.KnowledgeChatResponseVO;
import com.example.retinavision.pojo.VO.KnowledgeChatSessionVO;
import com.example.retinavision.pojo.VO.KnowledgeDocumentVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.KnowledgeChatService;
import com.example.retinavision.service.KnowledgeIngestionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.retinavision.constant.ErrorMessageSignal.FORBIDDEN;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeChatService chatService;

    public KnowledgeController(KnowledgeIngestionService ingestionService, KnowledgeChatService chatService) {
        this.ingestionService = ingestionService;
        this.chatService = chatService;
    }

    @PostMapping("/documents")
    public Result<KnowledgeDocumentVO> createDocument(@RequestBody KnowledgeDocumentCreateDTO request,
                                                      Authentication authentication) {
        CurrentUserVO user = currentUser(authentication);
        requireAdmin(user);
        return Result.success(toDocument(ingestionService.create(request, user.getId())));
    }

    @GetMapping("/documents")
    public Result<List<KnowledgeDocumentVO>> documents(Authentication authentication) {
        CurrentUserVO user = currentUser(authentication);
        requireAdmin(user);
        return Result.success(ingestionService.list().stream().map(this::toDocument).toList());
    }

    @PostMapping("/documents/{id}/reindex")
    public Result<KnowledgeDocumentVO> reindex(@PathVariable Long id, Authentication authentication) {
        CurrentUserVO user = currentUser(authentication);
        requireAdmin(user);
        return Result.success(toDocument(ingestionService.reindex(id)));
    }

    @PutMapping("/documents/{id}/status")
    public Result<KnowledgeDocumentVO> updateStatus(@PathVariable Long id,
                                                    @RequestBody KnowledgeDocumentStatusDTO request,
                                                    Authentication authentication) {
        CurrentUserVO user = currentUser(authentication);
        requireAdmin(user);
        return Result.success(toDocument(ingestionService.updateStatus(id, request.status())));
    }

    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id, Authentication authentication) {
        CurrentUserVO user = currentUser(authentication);
        requireAdmin(user);
        ingestionService.delete(id);
        return Result.success(null);
    }

    @PostMapping("/chat/sessions")
    public Result<KnowledgeChatSessionVO> createSession(Authentication authentication) {
        return Result.success(toSession(chatService.createSession(currentUser(authentication).getId())));
    }

    @GetMapping("/chat/sessions")
    public Result<List<KnowledgeChatSessionVO>> sessions(Authentication authentication) {
        return Result.success(chatService.listSessions(currentUser(authentication).getId()).stream().map(this::toSession).toList());
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    public Result<List<KnowledgeChatMessageVO>> messages(@PathVariable Long sessionId, Authentication authentication) {
        return Result.success(chatService.listMessages(sessionId, currentUser(authentication).getId()).stream().map(this::toMessage).toList());
    }

    @PostMapping("/chat")
    public Result<KnowledgeChatResponseVO> chat(@RequestBody KnowledgeChatRequestDTO request, Authentication authentication) {
        return Result.success(chatService.chat(request, currentUser(authentication)));
    }

    private CurrentUserVO currentUser(Authentication authentication) {
        return (CurrentUserVO) authentication.getPrincipal();
    }

    private void requireAdmin(CurrentUserVO user) {
        if (user.getRoleCode() != UserRole.ADMIN) {
            throw new BaseException(FORBIDDEN, "无权管理知识库");
        }
    }

    private KnowledgeDocumentVO toDocument(KnowledgeDocumentEntity entity) {
        return new KnowledgeDocumentVO(entity.getId(), entity.getTitle(), entity.getSource(), entity.getCategory(), entity.getAudience(),
                entity.getStatus(), entity.getVersion(), entity.getChunkCount(), entity.getFailureReason(),
                entity.getLastIndexedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private KnowledgeChatSessionVO toSession(KnowledgeChatSessionEntity entity) {
        return new KnowledgeChatSessionVO(entity.getId(), entity.getTitle(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private KnowledgeChatMessageVO toMessage(KnowledgeChatMessageEntity entity) {
        return new KnowledgeChatMessageVO(entity.getId(), entity.getSessionId(), entity.getRole(), entity.getContent(),
                entity.getCitationsJson(), entity.getCreatedAt());
    }
}
