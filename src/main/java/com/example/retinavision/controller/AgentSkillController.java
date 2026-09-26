package com.example.retinavision.controller;

import com.example.retinavision.pojo.DTO.UpdateActiveAgentSkillVersionDTO;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEvaluationRunEntity;
import com.example.retinavision.pojo.VO.AgentSkillVO;
import com.example.retinavision.pojo.VO.AgentSkillEvaluationVO;
import com.example.retinavision.pojo.VO.AgentSkillVersionVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.AgentSkillAdministrationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent-skills")
public class AgentSkillController {
    private final AgentSkillAdministrationService service;

    public AgentSkillController(AgentSkillAdministrationService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<AgentSkillVO>> list() {
        return Result.success(service.listSkills().stream().map(this::toVo).toList());
    }

    @GetMapping("/{skillCode}/versions")
    public Result<List<AgentSkillVersionVO>> versions(@PathVariable String skillCode) {
        AgentSkillEntity skill = service.listSkills().stream()
                .filter(item -> item.getSkillCode().equals(skillCode)).findFirst().orElse(null);
        Long active = skill == null ? null : skill.getActiveVersionId();
        return Result.success(service.listVersions(skillCode).stream().map(item ->
                new AgentSkillVersionVO(item.getId(), item.getVersion(), item.getRoutingExamplesJson(),
                        item.getWorkflowPrompt(), item.getAnswerStyle(), item.getId().equals(active),
                        item.getCreatedAt())).toList());
    }

    @PostMapping("/{skillCode}/versions/{versionId}/evaluate")
    public Result<AgentSkillEvaluationVO> evaluate(@PathVariable String skillCode,
                                                           @PathVariable Long versionId,
                                                           Authentication authentication) {
        CurrentUserVO user = (CurrentUserVO) authentication.getPrincipal();
        AgentSkillEvaluationRunEntity run = service.evaluate(skillCode, versionId, user.getId());
        return Result.success(new AgentSkillEvaluationVO(run.getId(), run.getStatus(), run.getTotalCount(),
                run.getRoutingAccuracy(), run.getParameterAccuracy(), Boolean.TRUE.equals(run.getSafetyPassed()),
                run.getFailureSamplesJson(), run.getCompletedAt()));
    }

    @PutMapping("/{skillCode}/active-version")
    public Result<Void> activate(@PathVariable String skillCode,
                                 @RequestBody UpdateActiveAgentSkillVersionDTO request) {
        service.activate(skillCode, request.versionId());
        return Result.success();
    }

    private AgentSkillVO toVo(AgentSkillEntity item) {
        return new AgentSkillVO(item.getSkillCode(), item.getName(), item.getDescription(), item.getStatus(),
                item.getActiveVersionId(), item.getUpdatedAt());
    }
}
