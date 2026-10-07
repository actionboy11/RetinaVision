package com.example.retinavision.pojo.VO;

import com.example.retinavision.agent.PatientQualityDisplayStatus;
import com.example.retinavision.enumeration.CaseWorkflowStatus;
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
