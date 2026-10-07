package com.example.retinavision.pojo.VO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PatientAgentReportDetailVO {
    private Long caseId;
    private String caseNo;
    private Long resultId;
    private Integer version;
    private LocalDateTime signedAt;
    private String signerName;
    private String findings;
    private String conclusion;
    private String recommendation;
}
