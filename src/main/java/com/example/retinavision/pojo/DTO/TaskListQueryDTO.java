package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskListQueryDTO {
    private Integer pageNo;
    private Integer pageSize;
    // 任务状态
    private TaskStatus status;
    private TaskType taskType;
    // 精确筛选病例编号；当前前端任务列表暂不传，保留给后续病例上下文筛选。
    private String caseNo;
    private  String keyword;
}
