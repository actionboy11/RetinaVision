package com.example.retinavision.pojo.cases.vo;

import com.example.retinavision.enumeration.cases.CaseWorkflowStatus;
import com.example.retinavision.enumeration.cases.EyeSide;
import com.example.retinavision.enumeration.patient.PatientGender;
import com.example.retinavision.enumeration.reporting.ReportStatus;
import com.example.retinavision.enumeration.clinical.ReviewStatus;
import com.example.retinavision.enumeration.analysis.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoctorAgentCaseSummaryVO {
    private Integer caseId;
    private String caseNo;
    private Long patientId;
    private String patientNo;
    private Integer patientAge;
    private PatientGender patientGender;
    private EyeSide eyeSide;
    private CaseWorkflowStatus workflowStatus;
    private String qualityStatus;
    private Double qualityScore;
    private TaskStatus segmentationStatus;
    private ReviewStatus reviewStatus;
    private ReportStatus reportStatus;
    private Long latestTaskId;
    private LocalDateTime updatedAt;
}
