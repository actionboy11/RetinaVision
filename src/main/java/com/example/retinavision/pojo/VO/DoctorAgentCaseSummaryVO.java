package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.PatientGender;
import com.example.retinavision.enumeration.TaskStatus;
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
    private Long latestTaskId;
    private LocalDateTime updatedAt;
}
