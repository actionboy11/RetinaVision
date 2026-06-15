package com.example.retinavision.controller;

import com.example.retinavision.pojo.DTO.CreateTaskDTO;
import com.example.retinavision.pojo.DTO.TaskListQueryDTO;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.CreateTaskVO;
import com.example.retinavision.pojo.VO.TaskDetailVO;
import com.example.retinavision.pojo.VO.TaskListItemVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analysis-tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }
    //分页查询任务
    @GetMapping
    public Result<PageResult<TaskListItemVO>> getTaskPage(
            @RequestBody TaskListQueryDTO taskListQueryDTO
            ) {
            PageResult<TaskListItemVO> taskList=taskService.getLTaskList(taskListQueryDTO);
            return Result.success(taskList);
    }
    //创建任务
    @PostMapping
    public Result<CreateTaskVO> createTask(
            @RequestBody CreateTaskDTO createTaskDTO) {
               CreateTaskVO createTaskVO= taskService.createTask(createTaskDTO);

               return Result.success(createTaskVO);

    }
    //查询任务详情
    @GetMapping("/{taskId}")
    public Result<TaskDetailVO> getTaskDetail(
            @PathVariable Integer taskId) {
        TaskDetailVO taskDetailVO=taskService.getTaskDetail(taskId);
        return Result.success(taskDetailVO);
    }

    //取消任务
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancelTask(
            @PathVariable Integer taskId) {
        taskService.cancelTask(taskId);
        return Result.success(null);
    }
    //重试任务
    @PostMapping("/{taskId}/retry")
    public Result<TaskDetailVO> retryTask(
            @PathVariable Integer taskId) {
        TaskDetailVO taskDetailVO=taskService.retryTask(taskId);
        return Result.success(taskDetailVO);
    }



}
