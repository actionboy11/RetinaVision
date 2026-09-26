package com.example.retinavision.controller;

import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PatientCaseProgressVO;
import com.example.retinavision.pojo.VO.PatientSignedReportVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.AnalysisReportService;
import com.example.retinavision.service.PatientCaseService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/cases/{caseId}")
public class PatientCaseController {
    private final PatientCaseService patientCases;
    private final AnalysisReportService reports;

    public PatientCaseController(PatientCaseService patientCases, AnalysisReportService reports) {
        this.patientCases = patientCases;
        this.reports = reports;
    }

    @GetMapping("/progress")
    public Result<PatientCaseProgressVO> progress(@PathVariable Long caseId, Authentication authentication) {
        return Result.success(patientCases.progress(caseId, user(authentication)));
    }

    @GetMapping("/signed-reports")
    public Result<List<PatientSignedReportVO>> signedReports(@PathVariable Long caseId,
                                                             Authentication authentication) {
        return Result.success(patientCases.signedReports(caseId, user(authentication)));
    }

    @GetMapping("/signed-reports/{resultId}/{version}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long caseId,
                                                         @PathVariable Long resultId,
                                                         @PathVariable Integer version,
                                                         Authentication authentication) throws IOException {
        patientCases.assertSignedReportAccessible(caseId, resultId, version, user(authentication));
        Path path = reports.getFile(resultId, version);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=retina-report-v" + version + ".pdf")
                .body(new InputStreamResource(Files.newInputStream(path)));
    }

    private CurrentUserVO user(Authentication authentication) {
        return (CurrentUserVO) authentication.getPrincipal();
    }
}
