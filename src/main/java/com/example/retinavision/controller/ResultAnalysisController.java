package com.example.retinavision.controller;

import com.example.retinavision.pojo.VO.AnalysisResultVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.AnalysisResultService;
import com.example.retinavision.service.AnalysisReportService;
import com.example.retinavision.enumeration.ReportStatus;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@RestController
@RequestMapping
public class ResultAnalysisController {
    private final AnalysisResultService analysisResultService;
    private final AnalysisReportService analysisReportService;

    public ResultAnalysisController(AnalysisResultService analysisResultService, AnalysisReportService analysisReportService) {
        this.analysisResultService = analysisResultService;
        this.analysisReportService = analysisReportService;
    }

    // 查询任务分析结果，JSON 业务接口仍然使用统一 Result<T> 包装。
    @GetMapping("/analysis-tasks/{taskId}/result")
    public Result<AnalysisResultVO> getAnalysisResult(@PathVariable Long taskId) {
        return Result.success(analysisResultService.getAnalysisResult(taskId));
    }

    // 预览分割结果图，文件接口直接返回二进制流，不使用 Result<T> 包装。
    //ResponseEntity 用于封装 HTTP 响应头和响应体。
    //contentType 根据文件类型设置响应头 Content-Type，InputStreamResource 用于将文件流返回给客户端。
    @GetMapping("/results/{resultId}/mask")
    public ResponseEntity<InputStreamResource> getMaskPreview(@PathVariable Long resultId) throws IOException {
        Path maskPath = analysisResultService.getResultMaskPath(resultId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(getMediaType(maskPath)))
                .body(new InputStreamResource(Files.newInputStream(maskPath)));
    }

    // 下载分析报告，Content-Disposition 会提示浏览器按文件下载。
    @GetMapping("/results/{resultId}/report")
    public ResponseEntity<InputStreamResource> downloadReport(@PathVariable Long resultId) throws IOException {
        Path reportPath = analysisReportService.list(resultId).stream()
                .filter(report -> report.getStatus() == ReportStatus.SIGNED || report.getStatus() == ReportStatus.SUPERSEDED)
                .max(java.util.Comparator.comparingInt(com.example.retinavision.pojo.Entity.AnalysisReportEntity::getVersion))
                .map(report -> analysisReportService.getFile(resultId, report.getVersion()))
                .orElseGet(() -> analysisResultService.getResultReportPath(resultId));
        String filename = "report-" + resultId + getExtension(reportPath);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(getMediaType(reportPath)))
                .body(new InputStreamResource(Files.newInputStream(reportPath)));
    }

    private String getMediaType(Path filePath) {
        //MediaType 根据文件扩展名判断
        String filename = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (filename.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF_VALUE;
        }
        if (filename.endsWith(".json")) {
            return MediaType.APPLICATION_JSON_VALUE;
        }
        if (filename.endsWith(".txt")) {
            return MediaType.TEXT_PLAIN_VALUE;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String getExtension(Path filePath) {
        String filename = filePath.getFileName().toString();
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0) {
            return ".pdf";
        }
        return filename.substring(lastDotIndex);
    }
}
