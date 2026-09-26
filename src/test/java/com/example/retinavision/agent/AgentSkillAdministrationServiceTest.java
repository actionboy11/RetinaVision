package com.example.retinavision.agent;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AgentSkillEvaluationRunMapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.AgentSkillVersionEntity;
import com.example.retinavision.service.impl.AgentSkillAdministrationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSkillAdministrationServiceTest {
    @Test
    void activationRequiresLatestPassingEvaluation() {
        AgentSkillMapper skills = mock(AgentSkillMapper.class);
        AgentSkillVersionMapper versions = mock(AgentSkillVersionMapper.class);
        AgentSkillEvaluationRunMapper evaluations = mock(AgentSkillEvaluationRunMapper.class);
        AgentSkillEntity skill = new AgentSkillEntity(); skill.setId(1L); skill.setSkillCode("ASSIGNED_CASE_SEARCH");
        AgentSkillVersionEntity version = new AgentSkillVersionEntity(); version.setId(11L); version.setSkillId(1L);
        AgentSkillEvaluationRunEntity run = new AgentSkillEvaluationRunEntity();
        run.setStatus("COMPLETED"); run.setRoutingAccuracy(0.89); run.setParameterAccuracy(1.0); run.setSafetyPassed(true);
        when(skills.selectOne(any())).thenReturn(skill);
        when(versions.selectById(11L)).thenReturn(version);
        when(evaluations.selectOne(any())).thenReturn(run);
        var service = new AgentSkillAdministrationServiceImpl(skills, versions, evaluations,
                new DefaultAgentSkillRouter(), new ObjectMapper());

        assertThatThrownBy(() -> service.activate("ASSIGNED_CASE_SEARCH", 11L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("评测");
    }
}
