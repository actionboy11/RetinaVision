package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskListQueryDTO {
    private Integer pageNo;
    private Integer pageSize;
    private TaskStatus taskStatus;
    private String caseNo;
    private  String keyword;
}
