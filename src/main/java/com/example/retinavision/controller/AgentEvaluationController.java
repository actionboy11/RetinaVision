package com.example.retinavision.controller;

import com.example.retinavision.agent.evaluation.*;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.DTO.ReviewAgentEvaluationDTO;
import com.example.retinavision.pojo.DTO.StartAgentEvaluationDTO;
import com.example.retinavision.pojo.VO.*;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.result.Result;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agent-evaluations")
public class AgentEvaluationController {
    private final AgentEvaluationService commands;
    private final AgentEvaluationAdministrationService administration;

    public AgentEvaluationController(AgentEvaluationService commands,
                                     AgentEvaluationAdministrationService administration) {
        this.commands = commands;
        this.administration = administration;
    }

    @GetMapping("/options")
    public Result<AgentEvaluationOptionsVO> options(Authentication authentication) {
        requireAdmin(authentication);
        return Result.success(administration.options());
    }

    @GetMapping("/datasets")
    public Result<List<AgentEvaluationDatasetVO>> datasets(Authentication authentication) {
        requireAdmin(authentication);
        return Result.success(administration.listDatasets());
    }

    @PostMapping("/runs")
    public Result<AgentEvaluationRunVO> start(@RequestBody StartAgentEvaluationDTO request,
                                              Authentication authentication) {
        CurrentUserVO user = requireAdmin(authentication);
        if (request == null) throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "评测配置不能为空");
        var run = commands.start(new AgentEvaluationStartCommand(request.datasetId(), request.targetRole(),
                request.modelKey(), request.skillVersions(), request.promptVersions(), user.getId()));
        return Result.success(administration.getRun(run.getId()));
    }

    @GetMapping("/runs")
    public Result<PageResult<AgentEvaluationRunVO>> runs(
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication authentication) {
        requireAdmin(authentication);
        return Result.success(administration.listRuns(targetRole, status, page, pageSize));
    }

    @GetMapping("/runs/{runId}")
    public Result<AgentEvaluationRunVO> run(@PathVariable Long runId, Authentication authentication) {
        requireAdmin(authentication);
        return Result.success(administration.getRun(runId));
    }

    @GetMapping("/runs/{runId}/results")
    public Result<PageResult<AgentEvaluationResultVO>> results(
            @PathVariable Long runId,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String errorType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication authentication) {
        requireAdmin(authentication);
        return Result.success(administration.listResults(runId, success, errorType, page, pageSize));
    }

    @PostMapping("/runs/{runId}/cancel")
    public Result<Void> cancel(@PathVariable Long runId, Authentication authentication) {
        requireAdmin(authentication);
        commands.requestCancel(runId);
        return Result.success();
    }

    @PutMapping("/runs/{runId}/review")
    public Result<AgentEvaluationRunVO> review(@PathVariable Long runId,
                                               @RequestBody ReviewAgentEvaluationDTO request,
                                               Authentication authentication) {
        CurrentUserVO user = requireAdmin(authentication);
        if (request == null) throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "评审结论不能为空");
        return Result.success(administration.review(runId, request.reviewDecision(),
                request.reviewNote(), user.getId()));
    }

    private CurrentUserVO requireAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserVO user)
                || user.getRoleCode() != UserRole.ADMIN) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权管理 Agent 评测");
        }
        return user;
    }
}
