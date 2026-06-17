package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.CreateTaskDTO;
import com.example.retinavision.pojo.DTO.TaskListQueryDTO;
import com.example.retinavision.pojo.VO.*;
import com.example.retinavision.result.PageResult;

import java.util.List;

public interface TaskService {
    PageResult<TaskListItemVO> getLTaskList(TaskListQueryDTO taskListQueryDTO);

    CreateTaskVO createTask(CreateTaskDTO createTaskDTO,Integer submittedBy);

    TaskDetailVO getTaskDetail(Integer taskId, Integer submittedBy);

    void cancelTask(Integer taskId, Integer id);

    RetryTaskVO retryTask(Integer taskId, Integer id);

    List<TaskLogVO> getTaskLog(Integer taskId, Integer id);
}
