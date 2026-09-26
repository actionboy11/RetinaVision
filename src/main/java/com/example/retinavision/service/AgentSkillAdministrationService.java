package com.example.retinavision.service;

import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.AgentSkillVersionEntity;

import java.util.List;

public interface AgentSkillAdministrationService {
    List<AgentSkillEntity> listSkills();
    List<AgentSkillVersionEntity> listVersions(String skillCode);
    AgentSkillEvaluationRunEntity evaluate(String skillCode, Long versionId, Integer operatorId);
    void activate(String skillCode, Long versionId);
}
