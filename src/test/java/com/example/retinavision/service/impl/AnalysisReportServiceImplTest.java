package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AnalysisReportMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.service.ReportPdfDocument;
import com.example.retinavision.service.ReportPdfRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisReportServiceImplTest {

    @TempDir
    Path root;

    private final AnalysisResultMapper results = mock(AnalysisResultMapper.class);
    private final AnalysisReportMapper reports = mock(AnalysisReportMapper.class);
    private final AnalysisReviewMapper reviews = mock(AnalysisReviewMapper.class);
    private final UserRegisterMapper users = mock(UserRegisterMapper.class);

    @Test
    void unapprovedResultCannotBeSigned() {
        when(results.selectById(1L)).thenReturn(new AnalysisResultEntity());
        AnalysisReviewEntity review = new AnalysisReviewEntity();
        review.setStatus(ReviewStatus.PENDING);
        when(reviews.selectOne(any())).thenReturn(review);

        assertThatThrownBy(() -> service().sign(1L, 2))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void signingStoresImmutableFileHash() throws Exception {
        AnalysisResultEntity result = new AnalysisResultEntity();
        result.setId(1L);
        when(results.selectById(1L)).thenReturn(result);
        when(reports.selectList(any())).thenReturn(List.of());
        when(reports.updateById(any(AnalysisReportEntity.class))).thenReturn(1);
        AnalysisReviewEntity review = new AnalysisReviewEntity();
        review.setStatus(ReviewStatus.APPROVED);
        when(reviews.selectOne(any())).thenReturn(review);
        UserEntity doctor = new UserEntity();
        doctor.setRealName("医生");
        doctor.setProfessionalNo("DOC-1");
        when(users.selectById(2)).thenReturn(doctor);

        AnalysisReportEntity signed = service().sign(1L, 2);

        assertThat(signed.getReportSha256()).hasSize(64);
        assertThat(Files.readAllBytes(root.resolve(signed.getReportObjectKey())))
                .isEqualTo("pdf".getBytes());
    }

    @Test
    void signingBuildsStructuredReportInsteadOfDumpingRawJson() {
        AnalysisResultEntity result = new AnalysisResultEntity();
        result.setId(1L);
        result.setTaskId(10L);
        result.setResultType(TaskType.VESSEL_SEGMENTATION);
        result.setResultJson("""
                {"conclusion":"已完成视网膜血管分割","vesselAreaRatio":0.077,"modelVersion":"model_new-v1"}
                """);
        result.setModelName("FSCNet_Final_DMI");
        result.setModelVersion("model_new-v1");
        result.setProcessingTimeMs(2465);
        when(results.selectById(1L)).thenReturn(result);

        AnalysisReportEntity draft = new AnalysisReportEntity();
        draft.setResultId(1L);
        draft.setVersion(1);
        draft.setStatus(ReportStatus.DRAFT);
        draft.setDraftJson("""
                {"findings":"血管分割清晰","conclusion":"可作为辅助参考","recommendation":"建议结合眼底检查复核","aiResult":"{\\"raw\\":true}"}
                """);
        when(reports.selectList(any())).thenReturn(List.of(draft));
        when(reports.updateById(any(AnalysisReportEntity.class))).thenReturn(1);

        AnalysisReviewEntity review = new AnalysisReviewEntity();
        review.setStatus(ReviewStatus.APPROVED);
        review.setFindings("医生所见优先");
        review.setConclusion("医生结论优先");
        review.setRecommendation("医生建议优先");
        when(reviews.selectOne(any())).thenReturn(review);

        UserEntity doctor = new UserEntity();
        doctor.setRealName("张医生");
        doctor.setProfessionalNo("DOC-2026-001");
        when(users.selectById(2)).thenReturn(doctor);

        AtomicReference<ReportPdfDocument> captured = new AtomicReference<>();
        ReportPdfRenderer renderer = (document, path) -> {
            captured.set(document);
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "pdf");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        };

        new AnalysisReportServiceImpl(results, reports, reviews, users, renderer, root.toString())
                .sign(1L, 2);

        ReportPdfDocument document = captured.get();
        assertThat(document.title()).isEqualTo("RetinaVision 眼底图像 AI 辅助分析报告");
        assertThat(document.doctorFields()).extracting(ReportPdfDocument.Field::value)
                .contains("医生所见优先", "医生结论优先", "医生建议优先");
        assertThat(document.analysisFields()).extracting(ReportPdfDocument.Field::value)
                .contains("已完成视网膜血管分割", "0.077", "FSCNet_Final_DMI", "model_new-v1", "2465 ms");
        assertThat(document.rawJson()).doesNotContain("\\\"raw\\\":true").doesNotContain("aiResult");
    }

    private AnalysisReportServiceImpl service() {
        ReportPdfRenderer renderer = (document, path) -> {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "pdf");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        };
        return new AnalysisReportServiceImpl(results, reports, reviews, users, renderer, root.toString());
    }
}
