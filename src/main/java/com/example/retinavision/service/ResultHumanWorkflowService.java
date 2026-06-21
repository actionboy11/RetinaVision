package com.example.retinavision.service;
import com.example.retinavision.pojo.DTO.SubmitFeedbackDTO;
import com.example.retinavision.pojo.Entity.AnalysisCorrectionEntity;
import com.example.retinavision.pojo.Entity.AnalysisFeedbackEntity;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
public interface ResultHumanWorkflowService {
    AnalysisFeedbackEntity addFeedback(Long resultId, SubmitFeedbackDTO request, Integer userId);
    List<AnalysisFeedbackEntity> listFeedback(Long resultId);
    AnalysisCorrectionEntity addCorrection(Long resultId, MultipartFile file, String reason, String correctedResultJson, Integer expectedVersion, Integer userId);
    List<AnalysisCorrectionEntity> listCorrections(Long resultId);
    AnalysisCorrectionEntity getCorrection(Long resultId, Integer version);
}
