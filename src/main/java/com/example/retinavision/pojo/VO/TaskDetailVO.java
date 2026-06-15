package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskDetailVO {
    private Long id;
    private String taskNo;
    private Long caseId;
    private  String caseNo;
    private Long imageFileId;
    private String originalFilename;
    private TaskType taskType;
    private TaskStatus status;
    private  int priority;
    private int retryCount;
    private int maxRetryCount;
    private String errorMessage;
    private Long submittedBy;
    private String submittedByName;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private  String imagePreviewUrl;
}
