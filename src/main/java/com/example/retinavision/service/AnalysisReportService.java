package com.example.retinavision.service;
import com.example.retinavision.pojo.DTO.UpdateReportDraftDTO;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import java.nio.file.Path;
import java.util.List;
// 分析报告服务接口 用于处理眼底图像分析报告的业务逻辑
public interface AnalysisReportService {
 //  获取或创建分析报告草稿
 AnalysisReportEntity getOrCreateDraft(Long resultId,Integer userId);
 //  更新分析报告草稿
 AnalysisReportEntity updateDraft(Long resultId,UpdateReportDraftDTO request,Integer userId);
 //  签署分析报告
 AnalysisReportEntity sign(Long resultId,Integer doctorId);
 //  获取分析报告列表
 List<AnalysisReportEntity> list(Long resultId);
 //  获取分析报告详情
 AnalysisReportEntity get(Long resultId,Integer version);
 //  获取分析报告文件路径
 Path getFile(Long resultId,Integer version);
}
