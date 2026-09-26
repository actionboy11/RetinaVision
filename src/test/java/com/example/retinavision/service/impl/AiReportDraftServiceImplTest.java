package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.llm.LlmException;
import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.mapper.AnalysisCorrectionMapper;
import com.example.retinavision.mapper.AnalysisReportMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.service.ClinicalTaskLogService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReportDraftServiceImplTest {

    private final AnalysisResultMapper results = mock(AnalysisResultMapper.class);
    private final AnalysisReportMapper reports = mock(AnalysisReportMapper.class);
    private final AnalysisReviewMapper reviews = mock(AnalysisReviewMapper.class);
    private final TaskMapper tasks = mock(TaskMapper.class);
    private final CaseMapper cases = mock(CaseMapper.class);
    private final ImageMapper images = mock(ImageMapper.class);
    private final AnalysisCorrectionMapper corrections = mock(AnalysisCorrectionMapper.class);
    private final ClinicalTaskLogService clinicalLogs = mock(ClinicalTaskLogService.class);

    @Test
    void generatesDraftFromStructuredResultAndStoresExplanationMetadata() {
        seedResultContext();
        AnalysisReportEntity draft = draft("{\"findings\":\"\",\"conclusion\":\"\",\"recommendation\":\"\",\"disclaimer\":\"AI辅助分析，不等同于独立医学诊断。\"}");
        when(reports.selectList(any())).thenReturn(List.of(draft));
        LlmOrchestrationService llm = (templateCode, userPrompt) -> {
            assertThat(templateCode).isEqualTo("REPORT_DRAFT_GENERATION");
            assertThat(userPrompt)
                    .contains("vesselAreaRatio")
                    .contains("qualityStatus")
                    .doesNotContain("C:\\")
                    .doesNotContain("patients/private")
                    .doesNotContain("original.png");
            return generated("""
                    {
                      "findings": "已完成视网膜血管分割，血管区域占比约 14.3%。",
                      "conclusion": "结果可作为辅助分析参考。",
                      "recommendation": "建议结合原始眼底图像和临床信息复核。",
                      "explanation": "本结果由结构化AI指标生成，不代表最终诊断。",
                      "disclaimer": "AI辅助分析，不等同于独立医学诊断。"
                    }
                    """);
        };

        AnalysisReportEntity generated = service(llm).generateDraft(1L, 9);

        assertThat(generated.getDraftJson())
                .contains("\"findings\":\"已完成视网膜血管分割，血管区域占比约 14.3%。\"")
                .contains("\"explanation\":\"本结果由结构化AI指标生成，不代表最终诊断。\"")
                .contains("\"llmProvider\":\"qwen\"")
                .contains("\"promptTemplateVersion\":1")
                .contains("\u0041\u0049\u8f85\u52a9\u5206\u6790\uff0c\u4e0d\u7b49\u540c\u4e8e\u72ec\u7acb\u533b\u5b66\u8bca\u65ad\u3002");
        verify(reports).updateById(draft);
        verify(clinicalLogs).appendResultEvent(1L, "AI 草稿已生成，仅供医生审核参考", "USER", 9);
    }

    @Test
    void invalidLlmJsonDoesNotOverwriteExistingDraft() {
        seedResultContext();
        AnalysisReportEntity draft = draft("{\"findings\":\"doctor text\",\"conclusion\":\"keep\",\"recommendation\":\"keep\"}");
        when(reports.selectList(any())).thenReturn(List.of(draft));
        LlmOrchestrationService llm = (templateCode, userPrompt) -> generated("not json");

        assertThatThrownBy(() -> service(llm).generateDraft(1L, 9))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("valid JSON");

        assertThat(draft.getDraftJson()).contains("doctor text");
        verify(reports, never()).updateById(any(AnalysisReportEntity.class));
    }

    @Test
    void llmFailureDoesNotOverwriteExistingDraft() {
        seedResultContext();
        AnalysisReportEntity draft = draft("{\"findings\":\"doctor text\"}");
        when(reports.selectList(any())).thenReturn(List.of(draft));
        LlmOrchestrationService llm = (templateCode, userPrompt) -> {
            throw new LlmException("LLM service unavailable");
        };

        assertThatThrownBy(() -> service(llm).generateDraft(1L, 9))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("unavailable");

        assertThat(draft.getDraftJson()).contains("doctor text");
        verify(reports, never()).updateById(any(AnalysisReportEntity.class));
    }

    @Test
    void unsafeDiagnosticWordingDoesNotOverwriteExistingDraft() {
        seedResultContext();
        AnalysisReportEntity draft = draft("{\"findings\":\"doctor text\",\"conclusion\":\"keep\",\"recommendation\":\"keep\"}");
        when(reports.selectList(any())).thenReturn(List.of(draft));
        LlmOrchestrationService llm = (templateCode, userPrompt) -> generated("""
                {
                  "findings": "血管分割结果提示异常。",
                  "conclusion": "诊断为糖尿病视网膜病变，已确诊。",
                  "recommendation": "无需复查。",
                  "explanation": "根据结构化结果生成。",
                  "disclaimer": "AI辅助分析，不等同于独立医学诊断。"
                }
                """);

        assertThatThrownBy(() -> service(llm).generateDraft(1L, 9))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("\u4e0d\u5408\u89c4\u8bca\u65ad\u63aa\u8f9e");

        assertThat(draft.getDraftJson()).contains("doctor text");
        verify(reports, never()).updateById(any(AnalysisReportEntity.class));
    }

    private AiReportDraftServiceImpl service(LlmOrchestrationService llm) {
        return new AiReportDraftServiceImpl(results, reports, reviews, tasks, cases, images, corrections,
                clinicalLogs, llm, new LlmSafetyPolicy());
    }

    private LlmGenerationResult generated(String content) {
        return new LlmGenerationResult(content, "REPORT_DRAFT_GENERATION", 1, "qwen", "qwen-plus", 12);
    }

    private AnalysisReportEntity draft(String draftJson) {
        AnalysisReportEntity draft = new AnalysisReportEntity();
        draft.setId(10L);
        draft.setResultId(1L);
        draft.setVersion(1);
        draft.setStatus(ReportStatus.DRAFT);
        draft.setDraftJson(draftJson);
        return draft;
    }

    private void seedResultContext() {
        AnalysisResultEntity result = new AnalysisResultEntity();
        result.setId(1L);
        result.setTaskId(2L);
        result.setResultType(TaskType.VESSEL_SEGMENTATION);
        result.setResultJson("{\"vesselAreaRatio\":0.143,\"conclusion\":\"done\"}");
        result.setModelName("FSCNet_Final_DMI");
        result.setModelVersion("model_new-v1");
        result.setProcessingTimeMs(3200);
        when(results.selectById(1L)).thenReturn(result);

        TaskEntity task = new TaskEntity();
        task.setId(2L);
        task.setCaseId(3L);
        task.setImageFileId(4L);
        when(tasks.selectById(2L)).thenReturn(task);

        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setId(3);
        caseEntity.setPatientAge(60);
        when(cases.selectById(3L)).thenReturn(caseEntity);

        ImageFileEntity image = new ImageFileEntity();
        image.setId(4L);
        image.setQualityStatus(ImageQualityStatus.PASS);
        image.setQualityScore(91.5);
        image.setImageWidth(1024);
        image.setImageHeight(768);
        image.setStorageObjectKey("patients/private/original.png");
        when(images.selectById(4L)).thenReturn(image);
    }
}
