package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.service.ReportPdfDocument;
import com.example.retinavision.service.ReportPdfRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

@Component
public class JdkReportPdfRenderer implements ReportPdfRenderer {

    private static final int PAGE_WIDTH = 1240;
    private static final int PAGE_HEIGHT = 1754;
    private static final int PDF_WIDTH = 595;
    private static final int PDF_HEIGHT = 842;
    private static final int MARGIN = 86;
    private static final int GAP = 28;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path fontPath;

    public JdkReportPdfRenderer(@Value("${retina.report.font-path:C:/Windows/Fonts/simhei.ttf}") String path) {
        this.fontPath = Paths.get(path).toAbsolutePath().normalize();
    }

    @Override
    public void render(ReportPdfDocument document, Path target) {
        if (!Files.isRegularFile(fontPath)) {
            throw new BaseException(ErrorMessageSignal.FILE_STORAGE_ERROR, "报告中文字体未配置或不存在");
        }
        try {
            Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontPath.toFile());
            BufferedImage page = new BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = page.createGraphics();
            configure(g);
            paint(document, g, baseFont);
            g.dispose();
            writePdf(page, target);
        } catch (FontFormatException | IOException exception) {
            throw new BaseException(ErrorMessageSignal.FILE_STORAGE_ERROR, "PDF 报告生成失败");
        }
    }

    private void configure(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private void paint(ReportPdfDocument document, Graphics2D g, Font baseFont) throws IOException {
        Font titleFont = baseFont.deriveFont(Font.BOLD, 35f);
        Font subtitleFont = baseFont.deriveFont(21f);
        Font sectionFont = baseFont.deriveFont(Font.BOLD, 22f);
        Font bodyFont = baseFont.deriveFont(18f);
        Font smallFont = baseFont.deriveFont(15f);

        drawHeader(g, document, titleFont, subtitleFont, smallFont);

        int y = 245;
        y = drawFieldCard(g, "病例与图像信息", document.caseFields(), MARGIN, y, 515, sectionFont, bodyFont) + GAP;
        y = drawFieldCard(g, "AI 分析摘要", document.analysisFields(), MARGIN + 545, 245, 523, sectionFont, bodyFont) + GAP;

        int doctorY = Math.max(y, 570);
        int doctorHeight = drawTextCard(g, "医生审核意见", document.doctorFields(), MARGIN, doctorY, 1068, sectionFont, bodyFont);

        int imageY = Math.max(doctorY + doctorHeight + GAP, 955);
        drawImages(g, document, imageY, sectionFont, bodyFont);
        drawFooter(g, document, smallFont);
    }

    private void drawHeader(Graphics2D g,
                            ReportPdfDocument document,
                            Font titleFont,
                            Font subtitleFont,
                            Font smallFont) {
        g.setColor(new Color(12, 74, 110));
        g.fillRoundRect(MARGIN, 58, 1068, 132, 28, 28);
        g.setColor(Color.WHITE);
        g.setFont(titleFont);
        g.drawString(document.title(), MARGIN + 36, 112);
        g.setFont(subtitleFont);
        g.drawString(document.disclaimer(), MARGIN + 36, 156);

        g.setFont(smallFont);
        g.drawString("报告编号：" + document.reportNo(), MARGIN + 735, 108);
        g.drawString("报告版本：V" + document.version(), MARGIN + 735, 139);
        String signedAt = document.signedAt() == null ? "-" : TIME_FORMATTER.format(document.signedAt());
        g.drawString("签发时间：" + signedAt, MARGIN + 735, 170);
    }

    private int drawFieldCard(Graphics2D g,
                              String title,
                              List<ReportPdfDocument.Field> fields,
                              int x,
                              int y,
                              int width,
                              Font sectionFont,
                              Font bodyFont) {
        g.setFont(bodyFont);
        int valueWidth = width - 200;
        int height = 78;
        for (ReportPdfDocument.Field field : fields) {
            height += rowHeight(g, field.value(), valueWidth, 2);
        }
        drawCard(g, x, y, width, height);
        drawSectionTitle(g, title, x + 24, y + 42, sectionFont);
        g.setFont(bodyFont);
        int rowY = y + 88;
        for (ReportPdfDocument.Field field : fields) {
            int consumed = drawLabelValue(g, field.label(), field.value(), x + 24, rowY, width - 48, bodyFont);
            rowY += consumed;
        }
        return y + height;
    }

    private int drawTextCard(Graphics2D g,
                             String title,
                             List<ReportPdfDocument.Field> fields,
                             int x,
                             int y,
                             int width,
                             Font sectionFont,
                             Font bodyFont) {
        int height = 330;
        drawCard(g, x, y, width, height);
        drawSectionTitle(g, title, x + 24, y + 42, sectionFont);
        int cursor = y + 88;
        for (ReportPdfDocument.Field field : fields) {
            g.setFont(bodyFont.deriveFont(Font.BOLD));
            g.setColor(new Color(71, 85, 105));
            g.drawString(field.label(), x + 24, cursor);
            g.setFont(bodyFont);
            g.setColor(new Color(15, 23, 42));
            cursor = drawWrappedText(g, field.value(), x + 138, cursor, width - 172, 27, 2) + 26;
        }
        return height;
    }

    private void drawImages(Graphics2D g, ReportPdfDocument document, int y, Font sectionFont, Font bodyFont) throws IOException {
        int cardWidth = 338;
        int cardHeight = 440;
        drawImageCard(g, "原始眼底图", document.originalImage(), MARGIN, y, cardWidth, cardHeight, sectionFont, bodyFont);
        drawImageCard(g, "AI 血管分割图", document.aiMask(), MARGIN + cardWidth + 28, y, cardWidth, cardHeight, sectionFont, bodyFont);
        drawImageCard(g, "人工修正图", document.correctedMask(), MARGIN + (cardWidth + 28) * 2, y, cardWidth, cardHeight, sectionFont, bodyFont);
    }

    private void drawImageCard(Graphics2D g,
                               String title,
                               Path path,
                               int x,
                               int y,
                               int width,
                               int height,
                               Font sectionFont,
                               Font bodyFont) throws IOException {
        drawCard(g, x, y, width, height);
        drawSectionTitle(g, title, x + 24, y + 42, sectionFont);
        int imageX = x + 24;
        int imageY = y + 78;
        int imageW = width - 48;
        int imageH = height - 112;
        g.setColor(new Color(248, 250, 252));
        g.fillRoundRect(imageX, imageY, imageW, imageH, 18, 18);
        g.setColor(new Color(203, 213, 225));
        g.drawRoundRect(imageX, imageY, imageW, imageH, 18, 18);
        if (path == null || !Files.isRegularFile(path)) {
            g.setFont(bodyFont);
            g.setColor(new Color(100, 116, 139));
            drawCentered(g, "无", imageX, imageY, imageW, imageH);
            return;
        }
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            g.setFont(bodyFont);
            g.setColor(new Color(100, 116, 139));
            drawCentered(g, "图像无法读取", imageX, imageY, imageW, imageH);
            return;
        }
        double scale = Math.min((double) (imageW - 18) / image.getWidth(), (double) (imageH - 18) / image.getHeight());
        int drawW = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int drawH = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int drawX = imageX + (imageW - drawW) / 2;
        int drawY = imageY + (imageH - drawH) / 2;
        g.drawImage(image, drawX, drawY, drawW, drawH, null);
    }

    private void drawFooter(Graphics2D g, ReportPdfDocument document, Font smallFont) {
        g.setFont(smallFont);
        g.setColor(new Color(100, 116, 139));
        g.drawLine(MARGIN, PAGE_HEIGHT - 112, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 112);
        g.drawString(document.disclaimer(), MARGIN, PAGE_HEIGHT - 76);
        g.drawString("RetinaVision 生成。请结合临床资料、医生检查和其他辅助检查综合判断。", MARGIN, PAGE_HEIGHT - 48);
    }

    private void drawCard(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, width, height, 22, 22);
        g.setColor(new Color(226, 232, 240));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, width, height, 22, 22);
    }

    private void drawSectionTitle(Graphics2D g, String title, int x, int y, Font font) {
        g.setFont(font);
        g.setColor(new Color(15, 23, 42));
        g.drawString(title, x, y);
    }

    private int drawLabelValue(Graphics2D g, String label, String value, int x, int y, int width, Font font) {
        g.setFont(font);
        g.setColor(new Color(100, 116, 139));
        g.drawString(label, x, y);
        g.setColor(new Color(15, 23, 42));
        List<String> lines = wrap(g, value, width - 152, 2);
        int cursor = y;
        for (String line : lines) {
            g.drawString(line, x + 152, cursor);
            cursor += 27;
        }
        return Math.max(44, lines.size() * 27 + 10);
    }

    private int drawWrappedText(Graphics2D g, String text, int x, int y, int width, int lineHeight, int maxLines) {
        List<String> lines = wrap(g, text == null ? "-" : text, width, maxLines);
        int cursor = y;
        for (String line : lines) {
            g.drawString(line, x, cursor);
            cursor += lineHeight;
        }
        return cursor - lineHeight;
    }

    private List<String> wrap(Graphics2D g, String text, int width, int maxLines) {
        FontMetrics metrics = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String next = current + String.valueOf(ch);
            if (metrics.stringWidth(next) > width && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(ch);
            if (lines.size() == maxLines) {
                break;
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add("-");
        }
        if (lines.size() == maxLines && metrics.stringWidth(lines.get(lines.size() - 1)) > width - metrics.stringWidth("...")) {
            String last = lines.get(lines.size() - 1);
            while (!last.isEmpty() && metrics.stringWidth(last + "...") > width) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(lines.size() - 1, last + "...");
        }
        return lines;
    }

    private int rowHeight(Graphics2D g, String value, int width, int maxLines) {
        return Math.max(44, wrap(g, value, width, maxLines).size() * 27 + 10);
    }

    private void drawCentered(Graphics2D g, String text, int x, int y, int width, int height) {
        FontMetrics metrics = g.getFontMetrics();
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = y + (height + metrics.getAscent()) / 2 - metrics.getDescent();
        g.drawString(text, textX, textY);
    }

    private void writePdf(BufferedImage image, Path target) throws IOException {
        byte[] rgb = new byte[image.getWidth() * image.getHeight() * 3];
        int offset = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y);
                rgb[offset++] = (byte) (color >> 16);
                rgb[offset++] = (byte) (color >> 8);
                rgb[offset++] = (byte) color;
            }
        }

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream zlib = new DeflaterOutputStream(compressed)) {
            zlib.write(rgb);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        ascii(out, "%PDF-1.4\n");
        obj(out, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        obj(out, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        obj(out, offsets, 3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PDF_WIDTH + " " + PDF_HEIGHT + "] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>");
        offsets.add(out.size());
        ascii(out, "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + image.getWidth()
                + " /Height " + image.getHeight()
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
                + compressed.size() + " >>\nstream\n");
        out.write(compressed.toByteArray());
        ascii(out, "\nendstream\nendobj\n");
        byte[] command = ("q " + PDF_WIDTH + " 0 0 " + PDF_HEIGHT + " 0 0 cm /Im0 Do Q")
                .getBytes(StandardCharsets.US_ASCII);
        offsets.add(out.size());
        ascii(out, "5 0 obj\n<< /Length " + command.length + " >>\nstream\n");
        out.write(command);
        ascii(out, "\nendstream\nendobj\n");

        int xref = out.size();
        ascii(out, "xref\n0 6\n0000000000 65535 f \n");
        for (int objectOffset : offsets) {
            ascii(out, String.format("%010d 00000 n \n", objectOffset));
        }
        ascii(out, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        Files.createDirectories(target.getParent());
        Files.write(target, out.toByteArray());
    }

    private void obj(ByteArrayOutputStream out, List<Integer> offsets, int id, String body) throws IOException {
        offsets.add(out.size());
        ascii(out, id + " 0 obj\n" + body + "\nendobj\n");
    }

    private void ascii(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }
}
