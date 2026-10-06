package com.example.retinavision.pojo.VO;

import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.enumeration.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoctorAgentTaskSummaryVO {
    private Long taskId;
    private String taskNo;
    private Integer caseId;
    private String caseNo;
    private String patientNo;
    private AnalysisTaskType taskType;
    private TaskStatus status;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String errorSummary;
    private LocalDateTime submittedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;
}
