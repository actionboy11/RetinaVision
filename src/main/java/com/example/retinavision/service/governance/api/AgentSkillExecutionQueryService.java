package com.example.retinavision.service.governance.api;

import com.example.retinavision.pojo.governance.vo.AgentSkillExecutionVO;

import java.util.List;

public interface AgentSkillExecutionQueryService {
    List<AgentSkillExecutionVO> latest(String skillCode, Boolean success);
}
