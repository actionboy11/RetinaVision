package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.AnalysisResultVO;

import java.nio.file.Path;

public interface AnalysisResultService {
    AnalysisResultVO getAnalysisResult(Long taskId);

    Path getResultMaskPath(Long resultId);

    Path getResultReportPath(Long resultId);
}
