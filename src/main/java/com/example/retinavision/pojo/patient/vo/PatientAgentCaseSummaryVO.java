package com.example.retinavision.pojo.patient.vo;

import com.example.retinavision.pojo.agent.PatientQualityDisplayStatus;
import com.example.retinavision.enumeration.cases.EyeSide;

import com.example.retinavision.pojo.agent.PatientQualityDisplayStatus;
import com.example.retinavision.enumeration.cases.CaseWorkflowStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PatientAgentCaseSummaryVO {
    private Long caseId;
    private String caseNo;
    private String eyeSide;
    private CaseWorkflowStatus workflowStatus;
    private PatientQualityDisplayStatus qualityStatus;
    private String qualityMessage;
    private boolean signedReportAvailable;
    private LocalDateTime updatedAt;
}
