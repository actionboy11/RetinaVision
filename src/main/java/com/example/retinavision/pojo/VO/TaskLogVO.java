package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.TaskStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskLogVO {
    private Integer id;
    private Integer taskId;
    private TaskStatus fromStatus;
    private TaskStatus toStatus;
    private String message;
    private String operatorType;
    private Integer operatorId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}
