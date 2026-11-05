package com.example.retinavision.service.clinical;
import com.example.retinavision.pojo.DTO.SubmitReviewDTO;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
public interface ResultReviewService {
    AnalysisReviewEntity getReview(Long resultId);
    AnalysisReviewEntity review(Long resultId, SubmitReviewDTO request, Integer doctorId);
}
