package com.example.retinavision.controller;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.DTO.UpdateActivePromptVersionDTO;
import com.example.retinavision.pojo.Entity.PromptTemplateEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.service.LlmCallLogService;
import com.example.retinavision.service.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptManagementControllerTest {
    private final PromptTemplateService templates = mock(PromptTemplateService.class);
    private final LlmCallLogService logs = mock(LlmCallLogService.class);
    private final PromptManagementController controller = new PromptManagementController(templates, logs);

    @Test
    void nonAdminCannotReadPromptTemplates() {
        BaseException exception = catchThrowableOfType(
                () -> controller.templates(auth(UserRole.DOCTOR)), BaseException.class);

        assertThat(exception.getCode()).isEqualTo(40300);
        verify(templates, never()).listTemplates();
    }

    @Test
    void adminCanSwitchTheActiveVersion() {
        PromptTemplateEntity template = new PromptTemplateEntity();
        template.setTemplateCode("REPORT_DRAFT_GENERATION");
        template.setActiveVersionId(12L);
        when(templates.activateVersion("REPORT_DRAFT_GENERATION", 12L)).thenReturn(template);

        var response = controller.activateVersion(
                "REPORT_DRAFT_GENERATION", new UpdateActivePromptVersionDTO(12L), auth(UserRole.ADMIN));

        assertThat(response.getData()).isTrue();
        verify(templates).activateVersion("REPORT_DRAFT_GENERATION", 12L);
    }

    @Test
    void versionResponseMarksTheTemplateSelectedVersionAsActive() {
        PromptTemplateVersionEntity version = new PromptTemplateVersionEntity();
        version.setId(7L);
        version.setTemplateCode("CASE_TREND_SUMMARY");
        version.setVersion(2);
        version.setSystemPrompt("system");
        version.setOutputContract("contract");
        version.setSafetyPolicy("policy");
        version.setActive(true);
        when(templates.listVersions("CASE_TREND_SUMMARY")).thenReturn(List.of(version));

        var response = controller.versions("CASE_TREND_SUMMARY", auth(UserRole.ADMIN));

        assertThat(response.getData()).singleElement().satisfies(item -> {
            assertThat(item.templateCode()).isEqualTo("CASE_TREND_SUMMARY");
            assertThat(item.active()).isTrue();
        });
    }

    private TestingAuthenticationToken auth(UserRole role) {
        return new TestingAuthenticationToken(new CurrentUserVO(1, "user", "User", role), null);
    }
}
