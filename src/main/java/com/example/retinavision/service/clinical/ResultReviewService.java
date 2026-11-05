package com.example.retinavision.service.clinical;
import com.example.retinavision.pojo.clinical.dto.SubmitReviewDTO;
import com.example.retinavision.pojo.clinical.entity.AnalysisReviewEntity;
public interface ResultReviewService {
    AnalysisReviewEntity getReview(Long resultId);
    AnalysisReviewEntity review(Long resultId, SubmitReviewDTO request, Integer doctorId);
}
