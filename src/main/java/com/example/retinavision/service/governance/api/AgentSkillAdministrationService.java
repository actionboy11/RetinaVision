package com.example.retinavision.service.governance.api;

import com.example.retinavision.pojo.agent.entity.AgentSkillEntity;
import com.example.retinavision.pojo.governance.entity.AgentSkillEvaluationRunEntity;
import com.example.retinavision.pojo.agent.entity.AgentSkillVersionEntity;

import java.util.List;

public interface AgentSkillAdministrationService {
    List<AgentSkillEntity> listSkills();
    List<AgentSkillVersionEntity> listVersions(String skillCode);
    AgentSkillEvaluationRunEntity evaluate(String skillCode, Long versionId, Integer operatorId);
    void activate(String skillCode, Long versionId);
}
