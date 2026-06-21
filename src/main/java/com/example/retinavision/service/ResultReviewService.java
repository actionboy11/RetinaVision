package com.example.retinavision.service;
import com.example.retinavision.pojo.DTO.SubmitReviewDTO;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
public interface ResultReviewService {
    AnalysisReviewEntity getReview(Long resultId);
    AnalysisReviewEntity review(Long resultId, SubmitReviewDTO request, Integer doctorId);
}
