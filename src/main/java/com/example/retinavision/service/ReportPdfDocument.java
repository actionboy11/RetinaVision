package com.example.retinavision.service;
import java.nio.file.Path;
public record ReportPdfDocument(String content, Path originalImage, Path aiMask, Path correctedMask) {}
