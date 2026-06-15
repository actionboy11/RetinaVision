package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateTaskVO {
    private Long id;
    private String taskNo;
    private TaskStatus taskStatus;
    private String errorMessage;;
}
