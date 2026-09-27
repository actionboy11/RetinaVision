package com.example.retinavision.agent;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.llm.PromptRenderService;
import com.example.retinavision.mapper.AgentChatMessageMapper;
import com.example.retinavision.mapper.AgentChatSessionMapper;
import com.example.retinavision.mapper.AgentSkillExecutionLogMapper;
import com.example.retinavision.pojo.DTO.AgentChatRequestDTO;
import com.example.retinavision.pojo.Entity.AgentChatMessageEntity;
import com.example.retinavision.pojo.Entity.AgentChatSessionEntity;
import com.example.retinavision.pojo.Entity.AgentSkillExecutionLogEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.pojo.VO.AgentChatResponseVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.service.PromptTemplateService;
import com.example.retinavision.service.LlmCallLogService;
import com.example.retinavision.service.impl.AgentChatServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.argThat;

class AgentChatServiceImplTest {
    private final AgentChatSessionMapper sessions = mock(AgentChatSessionMapper.class);
    private final AgentChatMessageMapper messages = mock(AgentChatMessageMapper.class);
    private final PromptTemplateService prompts = mock(PromptTemplateService.class);
    private final AgentModelGateway gateway = mock(AgentModelGateway.class);
    private final AgentToolFactory tools = mock(AgentToolFactory.class);
    private final LlmCallLogService callLogs = mock(LlmCallLogService.class);
    private final DoctorAgentSkillOrchestrator skillOrchestrator = mock(DoctorAgentSkillOrchestrator.class);
    private final AgentSkillExecutionLogMapper skillLogs = mock(AgentSkillExecutionLogMapper.class);
    private final AgentSkillVersionBindingService skillVersions = mock(AgentSkillVersionBindingService.class);

    @Test
    void doctorCanChatAndAgentPersistsBothMessages() {
        PromptTemplateVersionEntity version = new PromptTemplateVersionEntity();
        version.setSystemPrompt("仅执行只读工具");
        when(prompts.requireActiveVersion("CLINICAL_ASSISTANT_AGENT")).thenReturn(version);
        AgentToolBundle bundle = new AgentToolBundle(new ToolCallback[0]);
        when(tools.create(any(), any(), any(), any())).thenReturn(bundle);
        when(gateway.generate(any(), any(), any())).thenReturn("当前任务已经完成，建议医生结合结果复核。");
        when(skillVersions.resolve(any(), any())).thenReturn(new AgentSkillRuntimeVersion(51L, 2));

        AgentChatResponseVO response = service().chat(
                new AgentChatRequestDTO(null, "任务 12 现在是什么状态？"), doctor());

        assertThat(response.answer()).contains("结合结果复核");
        assertThat(response.disclaimer()).contains("只读辅助查询");
        assertThat(response.skill().code()).isEqualTo("MEDICAL_KNOWLEDGE_QA");
        assertThat(response.skill().version()).isEqualTo(2);
        verify(sessions).insert(any(AgentChatSessionEntity.class));
        verify(messages, times(2)).insert(any(AgentChatMessageEntity.class));
        verify(callLogs).record(any());
    }

    @Test
    void patientUsesPatientPrompt() {
        CurrentUserVO user = new CurrentUserVO(9, "user", "用户", UserRole.USER);
        PromptTemplateVersionEntity version = new PromptTemplateVersionEntity();
        version.setSystemPrompt("患者只读工具");
        when(prompts.requireActiveVersion("PATIENT_ASSISTANT_AGENT")).thenReturn(version);
        when(tools.create(any(), any(), any())).thenReturn(new AgentToolBundle(new ToolCallback[0]));
        when(gateway.generate(any(), any(), any())).thenReturn("检查仍在处理中，请耐心等待。");

        AgentChatResponseVO response = service().chat(new AgentChatRequestDTO(null, "我的检查到哪一步了？"), user);

        assertThat(response.answer()).contains("处理中");
        verify(prompts).requireActiveVersion("PATIENT_ASSISTANT_AGENT");
    }

    @Test
    void researcherCannotUseAgent() {
        CurrentUserVO user = new CurrentUserVO(9, "researcher", "研究员", UserRole.RESEARCHER);

        assertThatThrownBy(() -> service().chat(new AgentChatRequestDTO(null, "查询任务"), user))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("患者、医生或管理员");
    }

    @Test
    void doctorNaturalLanguageQueryUsesStructuredSkillResult() {
        DoctorAgentSkillResult skillResult = new DoctorAgentSkillResult(
                AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW, 0.98, "你目前负责 8 名患者。",
                new AgentStructuredData("METRICS", Map.of("patientCount", 8)), null, List.of());
        when(skillOrchestrator.handle(any(), any(), any())).thenReturn(Optional.of(skillResult));

        AgentChatResponseVO response = service().chat(new AgentChatRequestDTO(null, "我有多少名患者？"), doctor());

        assertThat(response.skill().code()).isEqualTo("DOCTOR_WORKLOAD_OVERVIEW");
        assertThat(response.data().type()).isEqualTo("METRICS");
        verify(gateway, times(0)).generate(any(), any(), any());
        verify(messages, times(2)).insert(any(AgentChatMessageEntity.class));
        verify(skillLogs).insert(any(AgentSkillExecutionLogEntity.class));
    }

    @Test
    void doctorSkillFailureIsAuditedBeforeTheErrorIsReturned() {
        when(skillOrchestrator.handle(any(), any(), any()))
                .thenThrow(new BaseException("查询上下文已过期"));

        assertThatThrownBy(() -> service().chat(new AgentChatRequestDTO(null, "继续"), doctor()))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("查询上下文已过期");

        verify(skillLogs).insert(argThat((AgentSkillExecutionLogEntity log) ->
                !Boolean.TRUE.equals(log.getSuccess()) && "BaseException".equals(log.getErrorType())));
        verify(messages, times(0)).insert(any(AgentChatMessageEntity.class));
    }

    private AgentChatServiceImpl service() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("qwen");
        properties.setModel("qwen-plus");
        return new AgentChatServiceImpl(sessions, messages, prompts, new PromptRenderService(), gateway,
                tools, new LlmSafetyPolicy(), properties, callLogs, skillOrchestrator,
                new com.fasterxml.jackson.databind.ObjectMapper(), skillLogs, skillVersions);
    }

    private CurrentUserVO doctor() {
        return new CurrentUserVO(7, "doctor", "医生", UserRole.DOCTOR);
    }
}
