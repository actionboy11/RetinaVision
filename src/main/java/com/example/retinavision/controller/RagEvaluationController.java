package com.example.retinavision.controller;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.RagEvaluationService;
import com.example.retinavision.pojo.Entity.PromptEvaluationRunEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PromptEvaluationRunVO;
import com.example.retinavision.result.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RagEvaluationController {
    private final RagEvaluationService service;
    private final ObjectMapper json;

    public RagEvaluationController(RagEvaluationService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping("/rag-evaluations/runs")
    public Result<PromptEvaluationRunVO> start(@RequestBody PromptEvaluationController.StartRequest request,
                                               Authentication authentication) {
        CurrentUserVO user = requireRole(authentication, UserRole.ADMIN);
        if (request == null || request.candidateVersionId() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请选择候选 RAG Prompt 版本");
        }
        return Result.success(toVo(service.start(request.candidateVersionId(), user.getId()), false));
    }

    @GetMapping("/rag-evaluations/runs")
    public Result<List<PromptEvaluationRunVO>> list(Authentication authentication) {
        requireViewer(authentication);
        return Result.success(service.list().stream().map(run -> toVo(run, false)).toList());
    }

    @GetMapping("/rag-evaluations/runs/{id}")
    public Result<PromptEvaluationRunVO> detail(@PathVariable Long id, Authentication authentication) {
        requireViewer(authentication);
        return Result.success(toVo(service.requireRun(id), true));
    }

    @PutMapping("/rag-evaluations/runs/{id}/review")
    public Result<PromptEvaluationRunVO> review(@PathVariable Long id,
                                                 @RequestBody PromptEvaluationController.ReviewRequest request,
                                                 Authentication authentication) {
        CurrentUserVO user = requireRole(authentication, UserRole.ADMIN);
        if (request == null || request.approved() == null || request.score() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请选择审核结论并填写评分");
        }
        return Result.success(toVo(service.review(id, request.approved(), request.score(), request.note(),
                user.getId()), true));
    }

    private PromptEvaluationRunVO toVo(PromptEvaluationRunEntity run, boolean includeResult) {
        JsonNode result = null;
        if (includeResult && run.getResultJson() != null) {
            try {
                result = json.readTree(run.getResultJson());
            } catch (Exception exception) {
                throw new BaseException(ErrorMessageSignal.SERVER_ERROR, "RAG 评测结果格式异常");
            }
        }
        return new PromptEvaluationRunVO(run.getId(), run.getTemplateCode(), run.getBaselineVersionId(),
                run.getCandidateVersionId(), run.getSampleVersion(), run.getProvider(), run.getModel(),
                run.getEmbeddingModel(), run.getScoreThreshold(),
                run.getStatus(), run.getAutomatedPass(), run.getReviewDecision(), run.getReviewScore(),
                run.getReviewNote(), run.getReviewedBy(), run.getReviewedAt(), run.getCreatedBy(),
                run.getCreatedAt(), run.getCompletedAt(), run.getFailureReason(), result);
    }

    private void requireViewer(Authentication authentication) {
        UserRole role = user(authentication).getRoleCode();
        if (role != UserRole.ADMIN) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权查看 RAG 评测");
        }
    }

    private CurrentUserVO requireRole(Authentication authentication, UserRole role) {
        CurrentUserVO user = user(authentication);
        if (user.getRoleCode() != role) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权操作 RAG 评测");
        }
        return user;
    }

    private CurrentUserVO user(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserVO user)) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "请先登录");
        }
        return user;
    }
}
