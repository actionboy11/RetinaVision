package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.AnalysisResultVO;

import java.nio.file.Path;

/**
 * 分析结果服务接口，定义了获取分析结果、获取结果掩码路径和获取结果报告路径的方法。
 * 该接口的实现类将负责管理分析结果的获取和路径的生成
 */
public interface AnalysisResultService {
    AnalysisResultVO getAnalysisResult(Long taskId);

    Path getResultMaskPath(Long resultId);

    Path getResultReportPath(Long resultId);
}
