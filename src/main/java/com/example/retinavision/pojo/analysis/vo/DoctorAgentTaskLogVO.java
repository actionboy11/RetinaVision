package com.example.retinavision.pojo.analysis.vo;

import com.example.retinavision.enumeration.analysis.TaskStatus;
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
