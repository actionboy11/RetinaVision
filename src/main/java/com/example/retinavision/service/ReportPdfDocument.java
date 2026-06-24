package com.example.retinavision.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public record ReportPdfDocument(
        String title,
        String reportNo,
        Integer version,
        LocalDateTime signedAt,
        String disclaimer,
        List<Field> caseFields,
        List<Field> analysisFields,
        List<Field> doctorFields,
        List<Field> technicalFields,
        String rawJson,
        Path originalImage,
        Path aiMask,
        Path correctedMask
) {
    public ReportPdfDocument {
        title = blankToDefault(title, "RetinaVision 眼底图像 AI 辅助分析报告");
        reportNo = blankToDefault(reportNo, "-");
        disclaimer = blankToDefault(disclaimer, "AI 辅助分析，不等同于独立医学诊断。");
        caseFields = immutable(caseFields);
        analysisFields = immutable(analysisFields);
        doctorFields = immutable(doctorFields);
        technicalFields = immutable(technicalFields);
        rawJson = "";
    }

    public record Field(String label, String value) {
        public Field {
            label = blankToDefault(label, "-");
            value = blankToDefault(value, "-");
        }
    }

    private static List<Field> immutable(List<Field> fields) {
        return fields == null ? List.of() : List.copyOf(fields);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
