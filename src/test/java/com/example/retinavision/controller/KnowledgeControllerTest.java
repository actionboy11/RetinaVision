package com.example.retinavision.controller;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.DTO.KnowledgeDocumentCreateDTO;
import com.example.retinavision.pojo.DTO.KnowledgeDocumentStatusDTO;
import com.example.retinavision.pojo.Entity.KnowledgeDocumentEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.service.KnowledgeChatService;
import com.example.retinavision.service.KnowledgeIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeControllerTest {
    private final KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
    private final KnowledgeChatService chatService = mock(KnowledgeChatService.class);
    private final KnowledgeController controller = new KnowledgeController(ingestionService, chatService);

    @Test
    void nonAdminCannotCreateKnowledgeDocument() {
        KnowledgeDocumentCreateDTO request = new KnowledgeDocumentCreateDTO("标题", "来源", "FAQ", "内容");

        BaseException exception = catchThrowableOfType(
                () -> controller.createDocument(request, auth(new CurrentUserVO(7, "u", "User", UserRole.USER))),
                BaseException.class);

        assertThat(exception.getCode()).isEqualTo(40300);
        verify(ingestionService, never()).create(request, 7);
    }

    @Test
    void adminCanCreateKnowledgeDocument() {
        KnowledgeDocumentCreateDTO request = new KnowledgeDocumentCreateDTO("标题", "来源", "FAQ", "内容");
        KnowledgeDocumentEntity document = document();
        when(ingestionService.create(request, 1)).thenReturn(document);

        var response = controller.createDocument(request, auth(new CurrentUserVO(1, "admin", "Admin", UserRole.ADMIN)));

        assertThat(response.getData().title()).isEqualTo("标题");
        assertThat(response.getData().category()).isEqualTo("FAQ");
        assertThat(response.getData().chunkCount()).isEqualTo(2);
        verify(ingestionService).create(request, 1);
    }

    @Test
    void adminCanUpdateKnowledgeDocumentStatus() {
        KnowledgeDocumentEntity document = document();
        document.setStatus("DISABLED");
        when(ingestionService.updateStatus(1L, "DISABLED")).thenReturn(document);

        var response = controller.updateStatus(1L, new KnowledgeDocumentStatusDTO("DISABLED"),
                auth(new CurrentUserVO(1, "admin", "Admin", UserRole.ADMIN)));

        assertThat(response.getData().status()).isEqualTo("DISABLED");
        verify(ingestionService).updateStatus(1L, "DISABLED");
    }

    private KnowledgeDocumentEntity document() {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(1L);
        document.setTitle("标题");
        document.setSource("来源");
        document.setCategory("FAQ");
        document.setStatus("ACTIVE");
        document.setVersion(1);
        document.setChunkCount(2);
        document.setLastIndexedAt(LocalDateTime.now());
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        return document;
    }

    private TestingAuthenticationToken auth(CurrentUserVO user) {
        return new TestingAuthenticationToken(user, null);
    }
}
