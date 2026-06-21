package com.example.retinavision.service;
import com.example.retinavision.pojo.DTO.UpdateReportDraftDTO;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import java.nio.file.Path;
import java.util.List;
public interface AnalysisReportService {
 AnalysisReportEntity getOrCreateDraft(Long resultId,Integer userId);
 AnalysisReportEntity updateDraft(Long resultId,UpdateReportDraftDTO request,Integer userId);
 AnalysisReportEntity sign(Long resultId,Integer doctorId);
 List<AnalysisReportEntity> list(Long resultId);
 AnalysisReportEntity get(Long resultId,Integer version);
 Path getFile(Long resultId,Integer version);
}
