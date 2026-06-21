package com.example.retinavision.controller;

import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.pojo.DTO.TaskListQueryDTO;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.VO.AnalysisResultVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.TaskListItemVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.AnalysisResultService;
import com.example.retinavision.service.ClinicalAccessService;
import com.example.retinavision.service.TaskService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/doctor/reviews")
public class DoctorReviewController {
    private final TaskService taskService;
    private final AnalysisResultService resultService;
    private final AnalysisResultMapper resultMapper;
    private final ClinicalAccessService accessService;

    public DoctorReviewController(TaskService taskService, AnalysisResultService resultService,
                                  AnalysisResultMapper resultMapper, ClinicalAccessService accessService) {
        this.taskService = taskService;
        this.resultService = resultService;
        this.resultMapper = resultMapper;
        this.accessService = accessService;
    }

    @GetMapping
    public Result<PageResult<TaskListItemVO>> list(@RequestParam(defaultValue = "1") Integer pageNo,
                                                  @RequestParam(defaultValue = "20") Integer pageSize,
                                                  Authentication authentication) {
        CurrentUserVO doctor = user(authentication);
        TaskListQueryDTO query = new TaskListQueryDTO(pageNo, pageSize, TaskStatus.SUCCESS,
                TaskType.VESSEL_SEGMENTATION, null, null);
        return Result.success(taskService.getLTaskList(query, doctor));
    }

    @GetMapping("/{resultId}")
    public Result<AnalysisResultVO> detail(@PathVariable Long resultId, Authentication authentication) {
        CurrentUserVO doctor = user(authentication);
        accessService.assertCanAccessResult(doctor, resultId);
        AnalysisResultEntity result = resultMapper.selectById(resultId);
        return Result.success(resultService.getAnalysisResult(result.getTaskId()));
    }

    private CurrentUserVO user(Authentication authentication) {
        return (CurrentUserVO) authentication.getPrincipal();
    }
}
