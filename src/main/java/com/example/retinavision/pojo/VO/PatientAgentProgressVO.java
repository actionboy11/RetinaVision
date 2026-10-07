package com.example.retinavision.pojo.VO;

import com.example.retinavision.agent.PatientQualityDisplayStatus;
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
