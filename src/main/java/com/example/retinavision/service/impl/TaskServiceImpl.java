package com.example.retinavision.service.impl;

import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.DTO.CreateTaskDTO;
import com.example.retinavision.pojo.DTO.TaskListQueryDTO;
import com.example.retinavision.pojo.VO.CreateTaskVO;
import com.example.retinavision.pojo.VO.TaskDetailVO;
import com.example.retinavision.pojo.VO.TaskListItemVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.TaskService;
import org.springframework.stereotype.Service;

@Service
public class TaskServiceImpl implements TaskService {

    private  final TaskMapper taskMapper;

    public TaskServiceImpl(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public PageResult<TaskListItemVO> getLTaskList(TaskListQueryDTO taskListQueryDTO) {
        return null;
    }

    @Override
    public CreateTaskVO createTask(CreateTaskDTO createTaskDTO) {
        return null;
    }

    @Override
    public TaskDetailVO getTaskDetail(Integer taskId) {
        return null;
    }

    @Override
    public void cancelTask(Integer taskId) {

    }

    @Override
    public TaskDetailVO retryTask(Integer taskId) {
        return null;
    }
}
