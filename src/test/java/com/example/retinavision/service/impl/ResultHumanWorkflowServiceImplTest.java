package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AnalysisCorrectionMapper;
import com.example.retinavision.mapper.AnalysisFeedbackMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.pojo.Entity.AnalysisCorrectionEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResultHumanWorkflowServiceImplTest {
    private static final Long RESULT_ID = 42L;

    @TempDir
    Path resultRoot;

    private AnalysisCorrectionMapper correctionMapper;
    private ResultHumanWorkflowServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        AnalysisResultMapper resultMapper = mock(AnalysisResultMapper.class);
        AnalysisFeedbackMapper feedbackMapper = mock(AnalysisFeedbackMapper.class);
        correctionMapper = mock(AnalysisCorrectionMapper.class);

        AnalysisResultEntity result = new AnalysisResultEntity();
        result.setId(RESULT_ID);
        result.setMaskObjectKey("tasks/7/mask.png");
        when(resultMapper.selectById(RESULT_ID)).thenReturn(result);
        when(correctionMapper.selectList(any())).thenReturn(List.of());

        Path originalMask = resultRoot.resolve(result.getMaskObjectKey());
        Files.createDirectories(originalMask.getParent());
        BufferedImage original = new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_BINARY);
        ImageIO.write(original, "png", originalMask.toFile());

        service = new ResultHumanWorkflowServiceImpl(
                resultMapper, feedbackMapper, correctionMapper, new ObjectMapper(), resultRoot.toString());
    }

    @Test
    void acceptsRealJpegMaskAndSavesItAsPng() throws Exception {
        BufferedImage jpegImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        jpegImage.setRGB(0, 0, 0x00ffffff);
        ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(jpegImage, "jpeg", jpegBytes)).isTrue();
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrected.jpg", "image/jpeg", jpegBytes.toByteArray());

        AnalysisCorrectionEntity correction = service.addCorrection(
                RESULT_ID, file, "manual correction", null, 0, 9);

        assertThat(correction.getCorrectedMaskObjectKey()).isEqualTo("corrections/42/v1.png");
        Path storedMask = resultRoot.resolve(correction.getCorrectedMaskObjectKey());
        assertThat(storedMask).exists();
        assertThat(Files.readAllBytes(storedMask)).startsWith(0x89, 0x50, 0x4e, 0x47);
        assertThat(ImageIO.read(storedMask.toFile())).isNotNull();
    }

    @Test
    void rejectsUndecodableMaskWithParamError() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "broken.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.addCorrection(
                RESULT_ID, file, "manual correction", null, 0, 9))
                .isInstanceOfSatisfying(BaseException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorMessageSignal.PARAM_ERROR));
    }
}
