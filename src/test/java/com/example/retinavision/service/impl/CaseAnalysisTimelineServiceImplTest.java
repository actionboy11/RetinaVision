package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmGenerationResult;
import com.example.retinavision.llm.LlmOrchestrationService;
import com.example.retinavision.llm.LlmSafetyPolicy;
import com.example.retinavision.mapper.AnalysisReportMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.AnalysisResultComparisonVO;
import com.example.retinavision.pojo.VO.CaseAnalysisTimelineVO;
import com.example.retinavision.pojo.VO.CaseTrendSummaryVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseAnalysisTimelineServiceImplTest {

    private final TaskMapper tasks = mock(TaskMapper.class);
    private final AnalysisResultMapper results = mock(AnalysisResultMapper.class);
    private final CaseMapper cases = mock(CaseMapper.class);
    private final ImageMapper images = mock(ImageMapper.class);
    private final AnalysisReviewMapper reviews = mock(AnalysisReviewMapper.class);
    private final AnalysisReportMapper reports = mock(AnalysisReportMapper.class);

    @Test
    void timelineCombinesTaskResultQualityReviewAndReportOrderedByFinishedTime() {
        LocalDateTime older = LocalDateTime.of(2026, 6, 1, 9, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 6, 2, 10, 0);
        TaskEntity first = task(10L, 1L, 100L, TaskType.VESSEL_SEGMENTATION, older);
        TaskEntity second = task(11L, 1L, 101L, TaskType.VESSEL_SEGMENTATION, newer);
        when(cases.selectById(1L)).thenReturn(caseEntity(1, EyeSide.LEFT));
        when(tasks.selectList(any())).thenReturn(List.of(second, first));
        when(results.selectList(any())).thenReturn(List.of(
                result(1000L, 10L, 0.0926, "FSCNet", "v1"),
                result(1001L, 11L, 0.1012, "FSCNet", "v2")
        ));
        when(images.selectBatchIds(List.of(100L, 101L))).thenReturn(List.of(
                image(100L, 88.5, ImageQualityStatus.PASS),
                image(101L, 91.5, ImageQualityStatus.PASS)
        ));
        AnalysisReviewEntity review = new AnalysisReviewEntity();
        review.setResultId(1001L);
        review.setStatus(ReviewStatus.APPROVED);
        when(reviews.selectList(any())).thenReturn(List.of(review));
        AnalysisReportEntity signed = new AnalysisReportEntity();
        signed.setResultId(1001L);
        signed.setStatus(ReportStatus.SIGNED);
        signed.setVersion(1);
        signed.setSignedAt(newer.plusMinutes(15));
        when(reports.selectList(any())).thenReturn(List.of(signed));

        CaseAnalysisTimelineVO timeline = service().timeline(1L, EyeSide.LEFT, TaskType.VESSEL_SEGMENTATION, null, null);

        assertThat(timeline.caseId()).isEqualTo(1L);
        assertThat(timeline.items()).extracting(CaseAnalysisTimelineVO.Item::taskId).containsExactly(10L, 11L);
        assertThat(timeline.items().get(0).qualityScore()).isEqualTo(88.5);
        assertThat(timeline.items().get(0).vesselAreaRatio()).isEqualTo(0.0926);
        assertThat(timeline.items().get(1).reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(timeline.items().get(1).reportStatus()).isEqualTo(ReportStatus.SIGNED);
    }

    @Test
    void timelineFiltersByEyeSideAndTaskTypeInMemoryForMapperCompatibility() {
        TaskEntity vesselLeft = task(10L, 1L, 100L, TaskType.VESSEL_SEGMENTATION, LocalDateTime.now());
        TaskEntity qualityLeft = task(11L, 1L, 100L, TaskType.IMAGE_QUALITY_CHECK, LocalDateTime.now());
        when(cases.selectById(1L)).thenReturn(caseEntity(1, EyeSide.LEFT));
        when(tasks.selectList(any())).thenReturn(List.of(vesselLeft, qualityLeft));
        when(results.selectList(any())).thenReturn(List.of(result(1000L, 10L, 0.1, "FSCNet", "v1")));
        when(images.selectBatchIds(List.of(100L))).thenReturn(List.of(image(100L, 90.0, ImageQualityStatus.PASS)));
        when(reviews.selectList(any())).thenReturn(List.of());
        when(reports.selectList(any())).thenReturn(List.of());

        CaseAnalysisTimelineVO timeline = service().timeline(1L, EyeSide.LEFT, TaskType.VESSEL_SEGMENTATION, null, null);

        assertThat(timeline.items()).extracting(CaseAnalysisTimelineVO.Item::taskId).containsExactly(10L);
    }

    @Test
    void comparisonRejectsResultsFromDifferentCaseEyeOrTaskType() {
        TaskEntity leftCaseOne = task(10L, 1L, 100L, TaskType.VESSEL_SEGMENTATION, LocalDateTime.now());
        TaskEntity rightCaseTwo = task(11L, 2L, 101L, TaskType.VESSEL_SEGMENTATION, LocalDateTime.now());
        AnalysisResultEntity baseline = result(1000L, 10L, 0.1, "FSCNet", "v1");
        AnalysisResultEntity target = result(1001L, 11L, 0.2, "FSCNet", "v1");
        when(results.selectById(1000L)).thenReturn(baseline);
        when(results.selectById(1001L)).thenReturn(target);
        when(tasks.selectById(10L)).thenReturn(leftCaseOne);
        when(tasks.selectById(11L)).thenReturn(rightCaseTwo);
        when(cases.selectById(1L)).thenReturn(caseEntity(1, EyeSide.LEFT));
        when(cases.selectById(2L)).thenReturn(caseEntity(2, EyeSide.RIGHT));
        when(images.selectById(100L)).thenReturn(image(100L, 90.0, ImageQualityStatus.PASS));
        when(images.selectById(101L)).thenReturn(image(101L, 90.0, ImageQualityStatus.PASS));

        assertThatThrownBy(() -> service().compare(1000L, 1001L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("同一病例、同一眼别、同类任务");
    }

    @Test
    void comparisonReturnsNullDeltasForMissingMetrics() {
        TaskEntity baselineTask = task(10L, 1L, 100L, TaskType.VESSEL_SEGMENTATION, LocalDateTime.now());
        TaskEntity targetTask = task(11L, 1L, 101L, TaskType.VESSEL_SEGMENTATION, LocalDateTime.now());
        AnalysisResultEntity baseline = result(1000L, 10L, null, "FSCNet", "v1");
        AnalysisResultEntity target = result(1001L, 11L, 0.2, "FSCNet", "v2");
        when(results.selectById(1000L)).thenReturn(baseline);
        when(results.selectById(1001L)).thenReturn(target);
        when(tasks.selectById(10L)).thenReturn(baselineTask);
        when(tasks.selectById(11L)).thenReturn(targetTask);
        when(cases.selectById(1L)).thenReturn(caseEntity(1, EyeSide.LEFT));
        when(images.selectById(100L)).thenReturn(image(100L, null, ImageQualityStatus.NOT_CHECKED));
        when(images.selectById(101L)).thenReturn(image(101L, 91.0, ImageQualityStatus.PASS));
        when(reviews.selectList(any())).thenReturn(List.of());
        when(reports.selectList(any())).thenReturn(List.of());

        AnalysisResultComparisonVO comparison = service().compare(1000L, 1001L);

        assertThat(comparison.vesselAreaRatioDelta()).isNull();
        assertThat(comparison.qualityScoreDelta()).isNull();
        assertThat(comparison.notes()).anyMatch(note -> note.contains("部分指标缺失"));
        assertThat(comparison.modelChanged()).isTrue();
    }

    @Test
    void trendSummaryUsesTheDatabaseManagedPromptScenario() {
        when(cases.selectById(1L)).thenReturn(caseEntity(1, EyeSide.LEFT));
        when(tasks.selectList(any())).thenReturn(List.of());
        LlmOrchestrationService llm = (templateCode, userContext) -> {
            assertThat(templateCode).isEqualTo("CASE_TREND_SUMMARY");
            assertThat(userContext).contains("\"caseId\":1").doesNotContain("storageObjectKey", "maskUrl");
            return new LlmGenerationResult(
                    "{\"summary\":\"当前仅有有限随访数据。\",\"recommendation\":\"建议结合临床复核。\"}",
                    templateCode, 4, "deepseek", "deepseek-chat", 20);
        };
        CaseAnalysisTimelineServiceImpl service = new CaseAnalysisTimelineServiceImpl(
                tasks, results, cases, images, reviews, reports, llm, new LlmSafetyPolicy());

        CaseTrendSummaryVO summary = service.generateTrendSummary(1L, EyeSide.LEFT, null);

        assertThat(summary.summary()).contains("有限随访数据");
        assertThat(summary.llmProvider()).isEqualTo("deepseek");
        assertThat(summary.llmModel()).isEqualTo("deepseek-chat");
    }

    private CaseAnalysisTimelineServiceImpl service() {
        return new CaseAnalysisTimelineServiceImpl(tasks, results, cases, images, reviews, reports);
    }

    private static TaskEntity task(Long id, Long caseId, Long imageId, TaskType type, LocalDateTime finishedAt) {
        TaskEntity entity = new TaskEntity();
        entity.setId(id);
        entity.setCaseId(caseId);
        entity.setImageFileId(imageId);
        entity.setTaskType(type);
        entity.setStatus(TaskStatus.SUCCESS);
        entity.setFinishedAt(finishedAt);
        entity.setSubmittedAt(finishedAt.minusMinutes(2));
        return entity;
    }

    private static AnalysisResultEntity result(Long id, Long taskId, Double ratio, String model, String version) {
        AnalysisResultEntity entity = new AnalysisResultEntity();
        entity.setId(id);
        entity.setTaskId(taskId);
        entity.setResultType(TaskType.VESSEL_SEGMENTATION);
        entity.setResultJson(ratio == null ? "{}" : "{\"vesselAreaRatio\":" + ratio + "}");
        entity.setModelName(model);
        entity.setModelVersion(version);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private static CaseEntity caseEntity(Integer id, EyeSide eyeSide) {
        CaseEntity entity = new CaseEntity();
        entity.setId(id);
        entity.setEyeSide(eyeSide);
        return entity;
    }

    private static ImageFileEntity image(Long id, Double score, ImageQualityStatus status) {
        ImageFileEntity entity = new ImageFileEntity();
        entity.setId(id);
        entity.setQualityScore(score);
        entity.setQualityStatus(status);
        entity.setQualityTaskId(700L + id);
        entity.setQualityResultId(800L + id);
        entity.setQualityCheckedAt(LocalDateTime.now());
        return entity;
    }
}
