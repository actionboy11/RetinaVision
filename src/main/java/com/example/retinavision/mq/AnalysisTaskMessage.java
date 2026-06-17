package com.example.retinavision.mq;

import com.example.retinavision.enumeration.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTaskMessage {

    private Long taskId;

    private String taskNo;

    private Long caseId;

    private Long imageFileId;

    private TaskType taskType;

    private Integer priority;

    private Integer submittedBy;

    private LocalDateTime submittedAt;
}
