package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.enumeration.TaskStatus;

import java.time.LocalDateTime;

public record PatientCaseProgressVO(Long caseId, String caseNo, String patientNo,
                                    CaseWorkflowStatus workflowStatus, int imageCount,
                                    ImageQualityStatus qualityStatus, TaskStatus analysisStatus,
                                    int signedReportCount, LocalDateTime updatedAt) {
}
