package com.example.retinavision.agent;

import com.example.retinavision.mapper.AgentSessionSkillVersionMapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.AgentSessionSkillVersionEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillVersionEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSkillVersionBindingServiceTest {
    @Test
    void newSessionBindsActiveVersionAndKeepsItAfterActivationChanges() {
        AgentSessionSkillVersionMapper bindings = mock(AgentSessionSkillVersionMapper.class);
        AgentSkillMapper skills = mock(AgentSkillMapper.class);
        AgentSkillVersionMapper versions = mock(AgentSkillVersionMapper.class);
        AgentSkillEntity skill = new AgentSkillEntity();
        skill.setId(3L);
        skill.setSkillCode("ASSIGNED_CASE_SEARCH");
        skill.setActiveVersionId(31L);
        AgentSkillVersionEntity version = new AgentSkillVersionEntity();
        version.setId(31L);
        version.setSkillId(3L);
        version.setVersion(2);
        when(skills.selectOne(any())).thenReturn(skill);
        when(versions.selectById(31L)).thenReturn(version);

        AgentSkillVersionBindingService service = new AgentSkillVersionBindingService(bindings, skills, versions);
        AgentSkillRuntimeVersion resolved = service.resolve(9L, AgentSkillCode.ASSIGNED_CASE_SEARCH);

        assertThat(resolved.version()).isEqualTo(2);
        verify(bindings).insert(any(AgentSessionSkillVersionEntity.class));
    }
}
