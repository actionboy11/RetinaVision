package com.example.retinavision.service.reporting;
import java.nio.file.Path;
public interface ReportPdfRenderer { void render(ReportPdfDocument document, Path target); }
