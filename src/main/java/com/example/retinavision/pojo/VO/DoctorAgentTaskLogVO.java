package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoctorAgentTaskLogVO {
    private TaskStatus fromStatus;
    private TaskStatus toStatus;
    private String message;
    private String operatorType;
    private LocalDateTime createdAt;
}
