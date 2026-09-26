package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.AgentSkillExecutionVO;

import java.util.List;

public interface AgentSkillExecutionQueryService {
    List<AgentSkillExecutionVO> latest(String skillCode, Boolean success);
}
