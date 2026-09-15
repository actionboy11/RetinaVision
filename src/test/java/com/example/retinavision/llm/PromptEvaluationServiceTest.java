package com.example.retinavision.llm;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.PromptEvaluationRunMapper;
import com.example.retinavision.mapper.PromptTemplateVersionMapper;
import com.example.retinavision.pojo.Entity.PromptEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.service.PromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.eq;

class PromptEvaluationServiceTest {
    @Test
    void doctorCannotApproveFailedAutomaticEvaluation() {
        PromptEvaluationRunMapper runs = mock(PromptEvaluationRunMapper.class);
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setTemplateCode(PromptEvaluationService.TEMPLATE_CODE);
        run.setStatus("COMPLETED");
        run.setAutomatedPass(false);
        when(runs.selectById(1L)).thenReturn(run);
        PromptEvaluationService service = service(runs);

        assertThatThrownBy(() -> service.review(1L, true, 5, "看过了", 8))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void doctorReviewIsStoredAfterSafeEvaluation() {
        PromptEvaluationRunMapper runs = mock(PromptEvaluationRunMapper.class);
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setTemplateCode(PromptEvaluationService.TEMPLATE_CODE);
        run.setStatus("COMPLETED");
        run.setAutomatedPass(true);
        when(runs.selectById(1L)).thenReturn(run);
        when(runs.saveReviewIfPending(eq(1L), eq("APPROVED"), eq(4), eq("措辞可用"), eq(8), any()))
                .thenReturn(1);
        PromptEvaluationService service = service(runs);

        service.review(1L, true, 4, "措辞可用", 8);

        assertThat(run.getDoctorDecision()).isEqualTo("APPROVED");
        assertThat(run.getDoctorScore()).isEqualTo(4);
        verify(runs).saveReviewIfPending(eq(1L), eq("APPROVED"), eq(4), eq("措辞可用"), eq(8), any());
    }

    @Test
    void alreadyReviewedRunCannotBeOverwritten() {
        PromptEvaluationRunMapper runs = mock(PromptEvaluationRunMapper.class);
        PromptEvaluationRunEntity run = new PromptEvaluationRunEntity();
        run.setTemplateCode(PromptEvaluationService.TEMPLATE_CODE);
        run.setStatus("COMPLETED");
        run.setAutomatedPass(true);
        run.setDoctorDecision("REJECTED");
        when(runs.selectById(1L)).thenReturn(run);

        assertThatThrownBy(() -> service(runs).review(1L, true, 5, "改判", 8))
                .isInstanceOf(BaseException.class).hasMessageContaining("已经复核");
    }

    @Test
    void runsBothVersionsAgainstEverySyntheticCaseAndStoresOnlyEvaluatedOutputs() {
        PromptEvaluationRunMapper runs = mock(PromptEvaluationRunMapper.class);
        PromptTemplateVersionMapper versions = mock(PromptTemplateVersionMapper.class);
        PromptTemplateService templates = mock(PromptTemplateService.class);
        LlmClient client = mock(LlmClient.class);
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setProvider("qwen");
        properties.setModel("test-model");
        PromptTemplateVersionEntity baseline = version(10L);
        PromptTemplateVersionEntity candidate = version(20L);
        when(templates.requireActiveVersion(PromptEvaluationService.TEMPLATE_CODE)).thenReturn(baseline);
        when(versions.selectById(10L)).thenReturn(baseline);
        when(versions.selectById(20L)).thenReturn(candidate);
        AtomicReference<PromptEvaluationRunEntity> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            PromptEvaluationRunEntity run = invocation.getArgument(0);
            run.setId(1L);
            saved.set(run);
            return 1;
        }).when(runs).insert(any(PromptEvaluationRunEntity.class));
        when(runs.selectById(1L)).thenAnswer(invocation -> saved.get());
        when(client.generateJson(any(), any())).thenReturn(
                "{\"findings\":\"分割结果供复核\",\"conclusion\":\"辅助分析\","
                        + "\"recommendation\":\"建议复核\",\"explanation\":\"指标解释\","
                        + "\"disclaimer\":\"AI辅助分析，不等同于独立医学诊断。\"}");
        PromptEvaluationService service = new PromptEvaluationService(runs, versions, templates, client,
                new PromptRenderService(), new ReportDraftEvaluationScorer(new ObjectMapper(),
                new LlmSafetyPolicy()), properties, new ObjectMapper(), Runnable::run);

        PromptEvaluationRunEntity run = service.start(20L, 7);

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getAutomatedPass()).isTrue();
        assertThat(run.getResultJson()).contains("quality-pass", "quality-warning", "missing-ratio",
                "vesselAreaRatio", "qualityStatus");
        verify(client, times(6)).generateJson(any(), any());
    }

    private PromptTemplateVersionEntity version(Long id) {
        PromptTemplateVersionEntity version = new PromptTemplateVersionEntity();
        version.setId(id);
        version.setTemplateId(2L);
        version.setSystemPrompt("synthetic test prompt");
        return version;
    }

    private PromptEvaluationService service(PromptEvaluationRunMapper runs) {
        Executor direct = Runnable::run;
        return new PromptEvaluationService(runs, mock(PromptTemplateVersionMapper.class),
                mock(PromptTemplateService.class), mock(LlmClient.class),
                new PromptRenderService(),
                new ReportDraftEvaluationScorer(new ObjectMapper(), new LlmSafetyPolicy()),
                new LlmProperties(), new ObjectMapper(), direct);
    }
}
