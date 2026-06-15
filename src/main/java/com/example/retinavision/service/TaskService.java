package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.CreateTaskDTO;
import com.example.retinavision.pojo.DTO.TaskListQueryDTO;
import com.example.retinavision.pojo.VO.CreateTaskVO;
import com.example.retinavision.pojo.VO.TaskDetailVO;
import com.example.retinavision.pojo.VO.TaskListItemVO;
import com.example.retinavision.result.PageResult;

public interface TaskService {
    PageResult<TaskListItemVO> getLTaskList(TaskListQueryDTO taskListQueryDTO);

    CreateTaskVO createTask(CreateTaskDTO createTaskDTO);

    TaskDetailVO getTaskDetail(Integer taskId);

    void cancelTask(Integer taskId);

    TaskDetailVO retryTask(Integer taskId);
}
