package com.example.retinavision.agent;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillVersionEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSkillCatalogServiceTest {

    @Test
    void loadsDoctorVisibleDefinitionsWithoutBindingEverySkill() {
        AgentSkillMapper skills = mock(AgentSkillMapper.class);
        AgentSkillVersionMapper versions = mock(AgentSkillVersionMapper.class);
        AgentSkillVersionBindingService bindings = mock(AgentSkillVersionBindingService.class);
        AgentSkillEntity task = skill(7L, AgentSkillCode.DOCTOR_TASK_SEARCH, 71L);
        AgentSkillEntity knowledge = skill(8L, AgentSkillCode.MEDICAL_KNOWLEDGE_QA, 81L);
        when(skills.selectList(any(Wrapper.class))).thenReturn(List.of(task, knowledge));
        when(bindings.findVersionId(12L, AgentSkillCode.DOCTOR_TASK_SEARCH)).thenReturn(Optional.of(72L));
        when(bindings.findVersionId(12L, AgentSkillCode.MEDICAL_KNOWLEDGE_QA)).thenReturn(Optional.empty());
        when(versions.selectById(72L)).thenReturn(version(72L, 7L, 3));
        when(versions.selectById(81L)).thenReturn(version(81L, 8L, 1));
        AgentSkillCatalogService service = new AgentSkillCatalogService(
                skills, versions, bindings, new AgentSkillRegistry());

        List<AgentSkillDefinition> definitions = service.availableFor(12L,
                new CurrentUserVO(27, "doctor", "医生", UserRole.DOCTOR));

        assertThat(definitions).extracting(AgentSkillDefinition::code)
                .containsExactly(AgentSkillCode.DOCTOR_TASK_SEARCH, AgentSkillCode.MEDICAL_KNOWLEDGE_QA);
        assertThat(definitions.get(0).version()).isEqualTo(3);
        verify(bindings).findVersionId(12L, AgentSkillCode.DOCTOR_TASK_SEARCH);
    }

    private AgentSkillEntity skill(long id, AgentSkillCode code, long activeVersionId) {
        AgentSkillEntity entity = new AgentSkillEntity();
        entity.setId(id);
        entity.setSkillCode(code.name());
        entity.setName(code.name());
        entity.setDescription("description");
        entity.setStatus("ACTIVE");
        entity.setActiveVersionId(activeVersionId);
        return entity;
    }

    private AgentSkillVersionEntity version(long id, long skillId, int version) {
        AgentSkillVersionEntity entity = new AgentSkillVersionEntity();
        entity.setId(id);
        entity.setSkillId(skillId);
        entity.setVersion(version);
        entity.setRoutingExamplesJson("[]");
        entity.setWorkflowPrompt("workflow");
        return entity;
    }
}
