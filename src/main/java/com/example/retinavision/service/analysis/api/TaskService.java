package com.example.retinavision.service.analysis.api;
import com.example.retinavision.pojo.analysis.vo.CreateTaskVO;
import com.example.retinavision.pojo.analysis.vo.RetryTaskVO;
import com.example.retinavision.pojo.analysis.vo.TaskDetailVO;
import com.example.retinavision.pojo.analysis.vo.TaskListItemVO;
import com.example.retinavision.pojo.analysis.vo.TaskLogVO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;

import com.example.retinavision.pojo.analysis.dto.CreateTaskDTO;
import com.example.retinavision.pojo.analysis.dto.TaskListQueryDTO;
import com.example.retinavision.result.PageResult;

import java.util.List;

public interface TaskService {
    PageResult<TaskListItemVO> getLTaskList(TaskListQueryDTO taskListQueryDTO, CurrentUserVO user);

    CreateTaskVO createTask(CreateTaskDTO createTaskDTO,Integer submittedBy);

    TaskDetailVO getTaskDetail(Integer taskId, Integer submittedBy);

    void cancelTask(Integer taskId, Integer id);

    RetryTaskVO retryTask(Integer taskId, Integer id);

    List<TaskLogVO> getTaskLog(Integer taskId, Integer id);
}
