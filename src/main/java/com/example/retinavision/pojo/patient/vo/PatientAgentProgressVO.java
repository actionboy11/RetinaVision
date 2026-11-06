package com.example.retinavision.pojo.patient.vo;

import com.example.retinavision.pojo.agent.PatientQualityDisplayStatus;

import com.example.retinavision.pojo.agent.PatientQualityDisplayStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PatientAgentProgressVO {
    private Long caseId;
    private String caseNo;
    private String currentStage;
    private List<PatientAgentProgressStageVO> stages;
    private PatientQualityDisplayStatus qualityStatus;
    private String qualityMessage;
    private String nextHandler;
    private String message;
    private LocalDateTime updatedAt;
    private LocalDateTime estimatedCompletionAt;
}
