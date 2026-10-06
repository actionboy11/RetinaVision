package com.example.retinavision.service.impl;

import com.example.retinavision.service.ReportPdfDocument;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdkReportPdfRendererTest {

    @Test
    void rendersPolishedPdfSmokeSample() throws Exception {
        Path outputDirectory = Path.of("target", "generated-test-reports");
        Files.createDirectories(outputDirectory);
        Path original = outputDirectory.resolve("sample-original.png");
        Path mask = outputDirectory.resolve("sample-mask.png");
        writeSampleImage(original, new Color(226, 232, 240), new Color(30, 64, 175));
        writeSampleImage(mask, Color.BLACK, Color.WHITE);

        ReportPdfDocument document = new ReportPdfDocument(
                "RetinaVision 眼底图像 AI 辅助分析报告",
                "RV-1-V1",
                1,
                LocalDateTime.of(2026, 6, 24, 9, 20, 0),
                "AI 辅助分析，不等同于独立医学诊断。",
                List.of(
                        new ReportPdfDocument.Field("病例号", "TEST202606240001"),
                        new ReportPdfDocument.Field("患者编码", "PTEST0001"),
                        new ReportPdfDocument.Field("眼别", "LEFT"),
                        new ReportPdfDocument.Field("图像质量", "PASS"),
                        new ReportPdfDocument.Field("质量评分", "87.5")
                ),
                List.of(
                        new ReportPdfDocument.Field("结果类型", "VESSEL_SEGMENTATION"),
                        new ReportPdfDocument.Field("AI 结论", "已完成视网膜血管分割，结果仅供辅助分析"),
                        new ReportPdfDocument.Field("血管面积比例", "0.077"),
                        new ReportPdfDocument.Field("模型名称", "FSCNet_Final_DMI"),
                        new ReportPdfDocument.Field("模型版本", "model_new-v1"),
                        new ReportPdfDocument.Field("处理耗时", "2465 ms")
                ),
                List.of(
                        new ReportPdfDocument.Field("审核医生", "张医生"),
                        new ReportPdfDocument.Field("医生编号", "DOC-2026-001"),
                        new ReportPdfDocument.Field("医生所见", "血管分割边界整体清晰，未见明显图像伪影影响主要判断。"),
                        new ReportPdfDocument.Field("审核结论", "可作为辅助参考。"),
                        new ReportPdfDocument.Field("处理建议", "建议结合眼底检查和临床资料复核。")
                ),
                List.of(new ReportPdfDocument.Field("报告版本", "V1")),
                "",
                original,
                mask,
                null
        );

        Path pdf = outputDirectory.resolve("sample-polished-report.pdf");
        new JdkReportPdfRenderer(reportFontPath()).render(document, pdf);

        assertThat(pdf).isRegularFile();
        assertThat(Files.readAllBytes(pdf)).startsWith("%PDF-1.4".getBytes());
        assertThat(Files.size(pdf)).isGreaterThan(20_000);
    }

    private String reportFontPath() {
        String configured = System.getenv("RETINA_REPORT_FONT_PATH");
        return configured == null || configured.isBlank()
                ? "C:/Windows/Fonts/simhei.ttf"
                : configured;
    }

    private void writeSampleImage(Path path, Color background, Color foreground) throws Exception {
        BufferedImage image = new BufferedImage(360, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(foreground);
        g.fillOval(70, 42, 220, 220);
        g.setColor(Color.WHITE);
        g.drawLine(110, 160, 260, 110);
        g.drawLine(130, 205, 290, 170);
        g.drawLine(170, 70, 185, 255);
        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }
}
