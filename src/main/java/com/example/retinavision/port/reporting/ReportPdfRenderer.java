package com.example.retinavision.port.reporting;

import com.example.retinavision.port.reporting.model.ReportPdfDocument;
import java.nio.file.Path;
public interface ReportPdfRenderer { void render(ReportPdfDocument document, Path target); }
