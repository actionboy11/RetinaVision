package com.example.retinavision.controller;

import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.pojo.DTO.TaskListQueryDTO;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.VO.AnalysisResultVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorReviewReminderVO;
import com.example.retinavision.pojo.VO.TaskListItemVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.AnalysisResultService;
import com.example.retinavision.service.ClinicalAccessService;
import com.example.retinavision.service.DoctorReviewReminderService;
import com.example.retinavision.service.TaskService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医生审核工作台 Controller。
 *
 * <p>这个 Controller 面向 DOCTOR 的独立审核入口：
 * 医生先看到已经完成 AI 血管分割的任务列表，再进入某个 resultId 的详情做审核、报告签发等操作。</p>
 *
 * <p>注意：真正的“保存审核、签发报告”等动作在 {@link ResultClinicalWorkflowController} 中。
 * 本类更像医生工作台的查询入口。</p>
 */
@RestController
@RequestMapping("/doctor/reviews")
public class DoctorReviewController {

    private final TaskService taskService;
    private final AnalysisResultService resultService;
    private final AnalysisResultMapper resultMapper;
    private final ClinicalAccessService accessService;
    private final DoctorReviewReminderService reminderService;

    public DoctorReviewController(TaskService taskService,
                                  AnalysisResultService resultService,
                                  AnalysisResultMapper resultMapper,
                                  ClinicalAccessService accessService,
                                  DoctorReviewReminderService reminderService) {
        this.taskService = taskService;
        this.resultService = resultService;
        this.resultMapper = resultMapper;
        this.accessService = accessService;
        this.reminderService = reminderService;
    }

    /**
     * 获取医生待办提醒摘要。
     *
     * <p>这个接口给前端侧边栏红点和在线弹窗使用，只读取当前任务、审核、报告状态，
     * 不会修改任何业务数据。权限由 SecurityConfig 中的 /doctor/** 医生限制兜底。</p>
     */
    @GetMapping("/reminders")
    public Result<DoctorReviewReminderVO> reminders(Authentication authentication) {
        currentUser(authentication);
        return Result.success(reminderService.getReminderSummary());
    }

    /**
     * 获取医生审核工作台列表。
     *
     * <p>当前只列出 SUCCESS + VESSEL_SEGMENTATION 的任务，
     * 因为只有血管分割成功后才会产生可审核的分析结果和 mask。</p>
     */
    @GetMapping
    public Result<PageResult<TaskListItemVO>> list(@RequestParam(defaultValue = "1") Integer pageNo,
                                                   @RequestParam(defaultValue = "20") Integer pageSize,
                                                   Authentication authentication) {
        CurrentUserVO doctor = currentUser(authentication);

        // 复用任务列表查询能力：医生工作台只是把筛选条件固定为“已完成的血管分割任务”。
        TaskListQueryDTO query = new TaskListQueryDTO(
                pageNo,
                pageSize,
                TaskStatus.SUCCESS,
                TaskType.VESSEL_SEGMENTATION,
                null,
                null
        );
        return Result.success(taskService.getLTaskList(query, doctor));
    }

    /**
     * 获取某个分析结果的详情。
     *
     * <p>这里 URL 使用 resultId，而结果详情服务使用 taskId 查询，所以需要先查一次 analysis_result。</p>
     */
    @GetMapping("/{resultId}")
    public Result<AnalysisResultVO> detail(@PathVariable Long resultId, Authentication authentication) {

        CurrentUserVO doctor = currentUser(authentication);
        // 医生、研究员、普通用户的数据访问范围不同；所有 resultId 入口都必须先走对象级权限校验。
        accessService.assertCanAccessResult(doctor, resultId);
        AnalysisResultEntity result = resultMapper.selectById(resultId);
        return Result.success(resultService.getAnalysisResult(result.getTaskId()));
    }

    /**
     * 从 Spring Security 的认证上下文中取出当前登录用户。
     * <p>JwtAuthenticationFilter 已经把 JWT 解析成 CurrentUserVO 放入 principal。</p>
     */
    private CurrentUserVO currentUser(Authentication authentication) {
        return (CurrentUserVO) authentication.getPrincipal();
    }
}
