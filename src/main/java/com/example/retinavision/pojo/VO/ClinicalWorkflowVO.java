package com.example.retinavision.pojo.VO;
import com.example.retinavision.enumeration.*;
import java.time.LocalDateTime;
public final class ClinicalWorkflowVO {
 private ClinicalWorkflowVO(){}
 public record Feedback(Long id,FeedbackVerdict verdict,String issueCodes,String comment,Integer submittedBy,LocalDateTime createdAt){}
 public record Correction(Long id,Integer version,String correctedResultJson,String correctedMaskObjectKey,String reason,CorrectionStatus status,Integer submittedBy,LocalDateTime createdAt,LocalDateTime updatedAt){}
 public record Review(Integer correctionVersion,ReviewStatus status,String findings,String conclusion,String recommendation,String reviewerNameSnapshot,String professionalNoSnapshot,Integer version,LocalDateTime reviewedAt){}
 public record Report(Integer version,ReportStatus status,Integer correctionVersion,String draftJson,String reportSha256,String signerNameSnapshot,String professionalNoSnapshot,LocalDateTime signedAt,LocalDateTime createdAt,LocalDateTime updatedAt){}
}
