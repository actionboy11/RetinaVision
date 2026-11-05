package com.example.retinavision.service.governance;

import com.example.retinavision.pojo.VO.AgentSkillExecutionVO;

import java.util.List;

public interface AgentSkillExecutionQueryService {
    List<AgentSkillExecutionVO> latest(String skillCode, Boolean success);
}
