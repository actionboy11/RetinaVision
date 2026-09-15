package com.example.retinavision.llm;

import com.example.retinavision.pojo.Entity.LlmCallLogEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.service.LlmCallLogService;
import com.example.retinavision.service.PromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmOrchestrationServiceImplTest {

    @Test
    void usesDatabaseTemplateAndRecordsSuccessfulCallMetadata() {
        PromptTemplateService templates = mock(PromptTemplateService.class);
        PromptRenderService renderer = new PromptRenderService();
        LlmClient client = mock(LlmClient.class);
        LlmCallLogService logs = mock(LlmCallLogService.class);
        LlmProperties properties = properties();
        when(templates.requireActiveVersion("REPORT_DRAFT_GENERATION")).thenReturn(version());
        when(client.generateJson("database system prompt", "sanitized context"))
                .thenReturn("{\"findings\":\"ok\"}");
        LlmOrchestrationServiceImpl service = new LlmOrchestrationServiceImpl(
                templates, renderer, client, logs, properties, new LlmSafetyPolicy(), new ObjectMapper());

        LlmGenerationResult result = service.generateJson("REPORT_DRAFT_GENERATION", "sanitized context");

        assertThat(result.content()).isEqualTo("{\"findings\":\"ok\"}");
        assertThat(result.templateVersion()).isEqualTo(2);
        assertThat(result.provider()).isEqualTo("qwen");
        ArgumentCaptor<LlmCallLogEntity> captor = ArgumentCaptor.forClass(LlmCallLogEntity.class);
        verify(logs).record(captor.capture());
        assertThat(captor.getValue().getSuccess()).isTrue();
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("REPORT_DRAFT_GENERATION");
        assertThat(captor.getValue().getErrorSummary()).isNull();
    }

    @Test
    void recordsSanitizedFailureWithoutPromptOrCredentialContent() {
        PromptTemplateService templates = mock(PromptTemplateService.class);
        LlmClient client = mock(LlmClient.class);
        LlmCallLogService logs = mock(LlmCallLogService.class);
        when(templates.requireActiveVersion("RAG_KNOWLEDGE_CHAT")).thenReturn(version());
        when(client.generateJson("database system prompt", "patient token sk-secret C:\\private\\image.jpg"))
                .thenThrow(new LlmException("provider rejected token sk-secret C:\\private\\image.jpg"));
        LlmOrchestrationServiceImpl service = new LlmOrchestrationServiceImpl(
                templates, new PromptRenderService(), client, logs, properties(),
                new LlmSafetyPolicy(), new ObjectMapper());

        assertThatThrownBy(() -> service.generateJson(
                "RAG_KNOWLEDGE_CHAT", "patient token sk-secret C:\\private\\image.jpg"))
                .isInstanceOf(LlmException.class);

        ArgumentCaptor<LlmCallLogEntity> captor = ArgumentCaptor.forClass(LlmCallLogEntity.class);
        verify(logs).record(captor.capture());
        LlmCallLogEntity log = captor.getValue();
        assertThat(log.getSuccess()).isFalse();
        assertThat(log.getErrorSummary())
                .doesNotContain("sk-secret", "C:\\private", "patient token")
                .contains("LlmException");
    }

    @Test
    void malformedModelOutputIsAuditedAsFailure() {
        PromptTemplateService templates = mock(PromptTemplateService.class);
        LlmClient client = mock(LlmClient.class);
        LlmCallLogService logs = mock(LlmCallLogService.class);
        PromptTemplateVersionEntity version = version();
        version.setOutputContract("{\"required\":[\"findings\"]}");
        when(templates.requireActiveVersion("REPORT_DRAFT_GENERATION")).thenReturn(version);
        when(client.generateJson("database system prompt", "sanitized context")).thenReturn("not-json");
        LlmOrchestrationServiceImpl service = new LlmOrchestrationServiceImpl(
                templates, new PromptRenderService(), client, logs, properties(),
                new LlmSafetyPolicy(), new ObjectMapper());

        assertThatThrownBy(() -> service.generateJson("REPORT_DRAFT_GENERATION", "sanitized context"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("valid JSON");

        ArgumentCaptor<LlmCallLogEntity> captor = ArgumentCaptor.forClass(LlmCallLogEntity.class);
        verify(logs).record(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
    }

    private PromptTemplateVersionEntity version() {
        PromptTemplateVersionEntity version = new PromptTemplateVersionEntity();
        version.setId(12L);
        version.setTemplateId(1L);
        version.setTemplateCode("REPORT_DRAFT_GENERATION");
        version.setVersion(2);
        version.setSystemPrompt("database system prompt");
        version.setActive(true);
        return version;
    }

    private LlmProperties properties() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("qwen");
        properties.setModel("qwen-plus");
        return properties;
    }
}
