package com.example.retinavision.pojo.patient.vo;

import com.example.retinavision.enumeration.cases.CaseWorkflowStatus;
import com.example.retinavision.enumeration.imaging.ImageQualityStatus;
import com.example.retinavision.enumeration.analysis.TaskStatus;

import java.time.LocalDateTime;

public record PatientCaseProgressVO(Long caseId, String caseNo, String patientNo,
                                    CaseWorkflowStatus workflowStatus, int imageCount,
                                    ImageQualityStatus qualityStatus, TaskStatus analysisStatus,
                                    int signedReportCount, LocalDateTime updatedAt) {
}
