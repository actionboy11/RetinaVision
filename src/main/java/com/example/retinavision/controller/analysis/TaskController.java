package com.example.retinavision.controller.analysis;

import com.example.retinavision.constant.shared.ErrorMessageSignal;
import com.example.retinavision.pojo.analysis.vo.CreateTaskVO;
import com.example.retinavision.pojo.analysis.vo.RetryTaskVO;
import com.example.retinavision.pojo.analysis.vo.TaskDetailVO;
import com.example.retinavision.pojo.analysis.vo.TaskListItemVO;
import com.example.retinavision.pojo.analysis.vo.TaskLogVO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;

import com.example.retinavision.pojo.analysis.dto.CreateTaskDTO;
import com.example.retinavision.pojo.analysis.dto.TaskListQueryDTO;
import com.example.retinavision.pojo.analysis.entity.TaskEntity;
import com.example.retinavision.enumeration.user.UserRole;
import com.example.retinavision.exception.shared.BaseException;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.analysis.api.TaskService;
import com.example.retinavision.service.cases.ClinicalAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analysis-tasks")
public class TaskController {

    private final TaskService taskService;
    private final ClinicalAccessService accessService;

    public TaskController(TaskService taskService, ClinicalAccessService accessService) {
        this.taskService = taskService;
        this.accessService = accessService;
    }
    //分页查询任务
    @GetMapping
    public Result<PageResult<TaskListItemVO>> getTaskPage(TaskListQueryDTO taskListQueryDTO, Authentication authentication) {
            CurrentUserVO currentUser = user(authentication);
            requireDoctor(currentUser);
            PageResult<TaskListItemVO> taskList=taskService.getLTaskList(taskListQueryDTO, currentUser);
            return Result.success(taskList);
    }
    //创建任务
    @PostMapping
    public Result<CreateTaskVO> createTask(
            @RequestBody CreateTaskDTO createTaskDTO,
            Authentication authentication) {
            CurrentUserVO tokenUser = (CurrentUserVO)authentication.getPrincipal();
            requireDoctor(tokenUser);
            accessService.assertCanAccessImage(tokenUser, createTaskDTO.getImageFileId());
            Integer id = tokenUser.getId();
            CreateTaskVO createTaskVO= taskService.createTask(createTaskDTO,id);
            return Result.success(createTaskVO);

    }
    //查询任务详情
    @GetMapping("/{taskId}")
    public Result<TaskDetailVO> getTaskDetail(
            @PathVariable Integer taskId,
            Authentication authentication) {
        CurrentUserVO tokenUser = (CurrentUserVO)authentication.getPrincipal();
        requireDoctor(tokenUser);
        accessService.assertCanAccessTask(tokenUser, taskId.longValue());
        Integer id = tokenUser.getId();
        TaskDetailVO taskDetailVO=taskService.getTaskDetail(taskId, id);
        return Result.success(taskDetailVO);
    }

    //取消任务
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancelTask(
            @PathVariable Integer taskId,
            Authentication authentication) {
        CurrentUserVO tokenUser = (CurrentUserVO)authentication.getPrincipal();
        requireDoctor(tokenUser);
        accessService.assertCanAccessTask(tokenUser, taskId.longValue());
        Integer id = tokenUser.getId();
        taskService.cancelTask(taskId,id);
        return Result.success(null);
    }
    //重试任务
    @PostMapping("/{taskId}/retry")
    public Result<RetryTaskVO> retryTask(
            @PathVariable Integer taskId,
            Authentication authentication
          ) {
        CurrentUserVO tokenUser = (CurrentUserVO)authentication.getPrincipal();
        requireDoctor(tokenUser);
        accessService.assertCanAccessTask(tokenUser, taskId.longValue());
        Integer id = tokenUser.getId();
        RetryTaskVO tryTaskVO=taskService.retryTask(taskId,id);
        return Result.success(tryTaskVO);
    }

    //查询任务日志
    @GetMapping({"/{taskId}/log", "/{taskId}/logs"})
    public Result<List<TaskLogVO>> getTaskLog(
            @PathVariable Integer taskId,
            Authentication authentication) {
        CurrentUserVO tokenUser = (CurrentUserVO)authentication.getPrincipal();
        requireDoctor(tokenUser);
        accessService.assertCanAccessTask(tokenUser, taskId.longValue());
        Integer id = tokenUser.getId();
        List<TaskLogVO> logList=taskService.getTaskLog(taskId,id);
        return Result.success(logList);
    }

    private CurrentUserVO user(Authentication authentication) {
        return (CurrentUserVO) authentication.getPrincipal();
    }

    private void requireDoctor(CurrentUserVO user) {
        if (user == null || user.getRoleCode() != UserRole.DOCTOR) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "仅负责医生可以操作分析任务");
        }
    }



}
