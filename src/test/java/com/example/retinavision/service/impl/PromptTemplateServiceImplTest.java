package com.example.retinavision.service.impl;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.PromptTemplateMapper;
import com.example.retinavision.mapper.PromptTemplateVersionMapper;
import com.example.retinavision.mapper.PromptEvaluationRunMapper;
import com.example.retinavision.pojo.Entity.PromptEvaluationRunEntity;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.llm.PromptEvaluationService;
import com.example.retinavision.llm.RagEvaluationService;
import com.example.retinavision.rag.EmbeddingProperties;
import com.example.retinavision.rag.QdrantProperties;
import com.example.retinavision.pojo.Entity.PromptTemplateEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.retinavision.agent.evaluation.AgentEvaluationEligibilityService;

class PromptTemplateServiceImplTest {
    private PromptTemplateMapper templates;
    private PromptTemplateVersionMapper versions;
    private PromptEvaluationRunMapper evaluations;
    private LlmProperties llmProperties;
    private PromptTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "prompt-eval-test"),
                PromptTemplateVersionEntity.class);
        templates = mock(PromptTemplateMapper.class);
        versions = mock(PromptTemplateVersionMapper.class);
        evaluations = mock(PromptEvaluationRunMapper.class);
        llmProperties = new LlmProperties();
        llmProperties.setProvider("qwen");
        llmProperties.setModel("qwen-plus");
        service = new PromptTemplateServiceImpl(templates, versions, evaluations, llmProperties,
                new EmbeddingProperties(), new QdrantProperties());
    }

    @Test
    void readsTheEnabledVersionSelectedByTemplateCode() {
        PromptTemplateEntity template = template(1L, 11L, "ACTIVE");
        PromptTemplateVersionEntity version = version(11L, 1L, 3, true);
        when(templates.selectOne(any())).thenReturn(template);
        when(versions.selectById(11L)).thenReturn(version);

        PromptTemplateVersionEntity active = service.requireActiveVersion("REPORT_DRAFT_GENERATION");

        assertThat(active.getVersion()).isEqualTo(3);
        assertThat(active.getSystemPrompt()).isEqualTo("system-v3");
    }

    @Test
    void missingActiveVersionFailsBeforeAnyLlmCallCanBeMade() {
        when(templates.selectOne(any())).thenReturn(template(1L, null, "ACTIVE"));

        assertThatThrownBy(() -> service.requireActiveVersion("REPORT_DRAFT_GENERATION"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("未配置启用版本");

        verify(versions, never()).selectById(any());
    }

    @Test
    void switchingVersionOnlyAcceptsAVersionOwnedByTheTemplate() {
        PromptTemplateEntity template = template(1L, 10L, "ACTIVE");
        when(templates.selectOne(any())).thenReturn(template);
        when(versions.selectById(22L)).thenReturn(version(22L, 2L, 2, false));

        assertThatThrownBy(() -> service.activateVersion("REPORT_DRAFT_GENERATION", 22L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("不属于该模板");

        verify(templates, never()).updateById(any(PromptTemplateEntity.class));
    }

    @Test
    void routerPromptActivationUsesUnifiedAgentEvaluationGate() {
        PromptTemplateEntity template = template(1L, 10L, "ACTIVE");
        template.setTemplateCode("AGENT_SKILL_ROUTER");
        when(templates.selectOne(any())).thenReturn(template);
        when(versions.selectById(20L)).thenReturn(version(20L, 1L, 2, false));
        AgentEvaluationEligibilityService eligibility = mock(AgentEvaluationEligibilityService.class);
        PromptTemplateServiceImpl governed = new PromptTemplateServiceImpl(
                templates, versions, evaluations, llmProperties,
                new EmbeddingProperties(), new QdrantProperties(), eligibility);

        governed.activateVersion("AGENT_SKILL_ROUTER", 20L);

        verify(eligibility).requirePromptEligible("AGENT_SKILL_ROUTER", 20L);
        verify(templates).updateById(template);
    }

    @Test
    void unpublishedReportVersionRequiresApprovedEvaluationAgainstCurrentVersion() {
        when(templates.selectOne(any())).thenReturn(template(1L, 10L, "ACTIVE"));
        when(versions.selectById(20L)).thenReturn(version(20L, 1L, 2, false));

        assertThatThrownBy(() -> service.activateVersion("REPORT_DRAFT_GENERATION", 20L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("评测");
        verify(templates, never()).updateById(any(PromptTemplateEntity.class));
    }

    @Test
    void approvedEvaluationAllowsPublishingCandidate() {
        when(templates.selectOne(any())).thenReturn(template(1L, 10L, "ACTIVE"));
        PromptTemplateVersionEntity candidate = version(20L, 1L, 2, false);
        when(versions.selectById(20L)).thenReturn(candidate);
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setStatus("COMPLETED");
        run.setAutomatedPass(true);
        run.setReviewDecision("APPROVED");
        run.setBaselineVersionId(10L);
        run.setCandidateVersionId(20L);
        run.setProvider("qwen");
        run.setModel("qwen-plus");
        run.setSampleVersion(PromptEvaluationService.SAMPLE_VERSION);
        when(evaluations.selectOne(any())).thenReturn(run);

        service.activateVersion("REPORT_DRAFT_GENERATION", 20L);

        assertThat(candidate.getReleasedAt()).isNotNull();
        verify(templates).updateById(any(PromptTemplateEntity.class));
    }

    @Test
    void changedModelInvalidatesEarlierApproval() {
        when(templates.selectOne(any())).thenReturn(template(1L, 10L, "ACTIVE"));
        when(versions.selectById(20L)).thenReturn(version(20L, 1L, 2, false));
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setBaselineVersionId(10L);
        run.setStatus("COMPLETED");
        run.setAutomatedPass(true);
        run.setReviewDecision("APPROVED");
        run.setProvider("deepseek");
        run.setModel("other-model");
        run.setSampleVersion(PromptEvaluationService.SAMPLE_VERSION);
        when(evaluations.selectOne(any())).thenReturn(run);

        assertThatThrownBy(() -> service.activateVersion("REPORT_DRAFT_GENERATION", 20L))
                .isInstanceOf(BaseException.class).hasMessageContaining("评测");
    }

    @Test
    void releasedOlderVersionCanBeRestoredWithoutNewEvaluation() {
        when(templates.selectOne(any())).thenReturn(template(1L, 20L, "ACTIVE"));
        PromptTemplateVersionEntity previous = version(10L, 1L, 1, false);
        previous.setReleasedAt(java.time.LocalDateTime.now().minusDays(1));
        when(versions.selectById(10L)).thenReturn(previous);

        service.activateVersion("REPORT_DRAFT_GENERATION", 10L);

        verify(evaluations, never()).selectOne(any());
        verify(templates).updateById(any(PromptTemplateEntity.class));
    }

    @Test
    void unpublishedRagVersionNeedsApprovedCurrentEvaluation() {
        PromptTemplateEntity rag = template(2L, 30L, "ACTIVE");
        rag.setTemplateCode(RagEvaluationService.TEMPLATE_CODE);
        when(templates.selectOne(any())).thenReturn(rag);
        when(versions.selectById(40L)).thenReturn(version(40L, 2L, 2, false));

        assertThatThrownBy(() -> service.activateVersion(RagEvaluationService.TEMPLATE_CODE, 40L))
                .isInstanceOf(BaseException.class).hasMessageContaining("评测");
        verify(templates, never()).updateById(any(PromptTemplateEntity.class));
    }

    @Test
    void approvedRagEvaluationAllowsActivation() {
        PromptTemplateEntity rag = template(2L, 30L, "ACTIVE");
        rag.setTemplateCode(RagEvaluationService.TEMPLATE_CODE);
        when(templates.selectOne(any())).thenReturn(rag);
        when(versions.selectById(40L)).thenReturn(version(40L, 2L, 2, false));
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setStatus("COMPLETED");
        run.setAutomatedPass(true);
        run.setReviewDecision("APPROVED");
        run.setBaselineVersionId(30L);
        run.setCandidateVersionId(40L);
        run.setProvider("qwen");
        run.setModel("qwen-plus");
        run.setSampleVersion(RagEvaluationService.SAMPLE_VERSION);
        run.setEmbeddingModel("text-embedding-v4");
        run.setScoreThreshold(0.2);
        when(evaluations.selectOne(any())).thenReturn(run);

        service.activateVersion(RagEvaluationService.TEMPLATE_CODE, 40L);

        verify(templates).updateById(any(PromptTemplateEntity.class));
    }

    @Test
    void changedEmbeddingConfigurationInvalidatesRagApproval() {
        PromptTemplateEntity rag = template(2L, 30L, "ACTIVE");
        rag.setTemplateCode(RagEvaluationService.TEMPLATE_CODE);
        when(templates.selectOne(any())).thenReturn(rag);
        when(versions.selectById(40L)).thenReturn(version(40L, 2L, 2, false));
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setStatus("COMPLETED");
        run.setAutomatedPass(true);
        run.setReviewDecision("APPROVED");
        run.setBaselineVersionId(30L);
        run.setProvider("qwen");
        run.setModel("qwen-plus");
        run.setSampleVersion(RagEvaluationService.SAMPLE_VERSION);
        run.setEmbeddingModel("another-embedding-model");
        run.setScoreThreshold(0.2);
        when(evaluations.selectOne(any())).thenReturn(run);

        assertThatThrownBy(() -> service.activateVersion(RagEvaluationService.TEMPLATE_CODE, 40L))
                .isInstanceOf(BaseException.class).hasMessageContaining("评测");
    }

    private PromptTemplateEntity template(Long id, Long activeVersionId, String status) {
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(id);
        entity.setTemplateCode("REPORT_DRAFT_GENERATION");
        entity.setStatus(status);
        entity.setActiveVersionId(activeVersionId);
        return entity;
    }

    private PromptTemplateVersionEntity version(Long id, Long templateId, int version, boolean active) {
        PromptTemplateVersionEntity entity = new PromptTemplateVersionEntity();
        entity.setId(id);
        entity.setTemplateId(templateId);
        entity.setVersion(version);
        entity.setSystemPrompt("system-v" + version);
        entity.setActive(active);
        return entity;
    }
}
