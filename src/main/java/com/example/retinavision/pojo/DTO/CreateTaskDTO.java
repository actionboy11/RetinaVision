package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateTaskDTO {
    private Long caseId;
    private  Long imageFileId;
    private TaskType taskType;
    private int priority;
    private Boolean qualityOverride = false;
    private String qualityOverrideReason;

    public CreateTaskDTO(Long caseId, Long imageFileId, TaskType taskType, int priority) {
        this.caseId = caseId;
        this.imageFileId = imageFileId;
        this.taskType = taskType;
        this.priority = priority;
    }
}
