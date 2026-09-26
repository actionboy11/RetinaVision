package com.example.retinavision.controller;

import com.example.retinavision.pojo.VO.AgentSkillExecutionVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.AgentSkillExecutionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent-skill-executions")
public class AgentSkillExecutionController {
    private final AgentSkillExecutionQueryService service;

    public AgentSkillExecutionController(AgentSkillExecutionQueryService service) { this.service = service; }

    @GetMapping
    public Result<List<AgentSkillExecutionVO>> list(
            @RequestParam(required = false) String skillCode,
            @RequestParam(required = false) Boolean success) {
        return Result.success(service.latest(skillCode, success));
    }
}
