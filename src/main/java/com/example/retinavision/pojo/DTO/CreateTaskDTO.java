package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTaskDTO {
    private Long caseId;
    private  Long imageFileId;
    private TaskType taskType;
    private int priority;
}
