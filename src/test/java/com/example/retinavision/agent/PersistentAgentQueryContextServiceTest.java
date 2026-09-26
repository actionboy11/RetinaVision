package com.example.retinavision.agent;

import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.mapper.AgentQueryContextMapper;
import com.example.retinavision.pojo.Entity.AgentQueryContextEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistentAgentQueryContextServiceTest {

    @Test
    void savesAllDoctorCaseFilters() {
        AgentQueryContextMapper mapper = mock(AgentQueryContextMapper.class);
        when(mapper.selectById(12L)).thenReturn(null);
        PersistentAgentQueryContextService service = new PersistentAgentQueryContextService(mapper, new ObjectMapper());

        service.save(12L, context());

        ArgumentCaptor<AgentQueryContextEntity> entity = ArgumentCaptor.forClass(AgentQueryContextEntity.class);
        verify(mapper).insert(entity.capture());
        assertThat(entity.getValue().getCurrentFiltersJson())
                .contains("\"segmentationState\":\"FAILED\"")
                .contains("\"clinicalState\":\"PENDING_REVIEW\"")
                .contains("\"dateWindow\":\"LAST_30_DAYS\"")
                .contains("\"eyeSide\":\"LEFT\"");
    }

    @Test
    void restoresAllDoctorCaseFilters() {
        AgentQueryContextMapper mapper = mock(AgentQueryContextMapper.class);
        AgentQueryContextEntity entity = new AgentQueryContextEntity();
        entity.setSessionId(12L);
        entity.setCurrentSkillCode(AgentSkillCode.ASSIGNED_CASE_SEARCH.name());
        entity.setCurrentFiltersJson("{\"segmentationState\":\"FAILED\",\"clinicalState\":\"PENDING_REVIEW\","
                + "\"dateWindow\":\"LAST_30_DAYS\",\"eyeSide\":\"LEFT\"}");
        entity.setCurrentPage(2);
        entity.setPageSize(10);
        entity.setTotal(22L);
        entity.setRecentResultReferencesJson("[3,4]");
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        when(mapper.selectById(12L)).thenReturn(entity);
        PersistentAgentQueryContextService service = new PersistentAgentQueryContextService(mapper, new ObjectMapper());

        AgentQueryContextSnapshot restored = service.load(12L).orElseThrow();

        assertThat(restored.segmentationState()).isEqualTo(SegmentationState.FAILED);
        assertThat(restored.clinicalState()).isEqualTo(DoctorClinicalState.PENDING_REVIEW);
        assertThat(restored.dateWindow()).isEqualTo(DoctorDateWindow.LAST_30_DAYS);
        assertThat(restored.eyeSide()).isEqualTo(EyeSide.LEFT);
        assertThat(restored.recentCaseIds()).containsExactly(3, 4);
    }

    private AgentQueryContextSnapshot context() {
        return new AgentQueryContextSnapshot(AgentSkillCode.ASSIGNED_CASE_SEARCH, SegmentationState.FAILED,
                DoctorClinicalState.PENDING_REVIEW, DoctorDateWindow.LAST_30_DAYS, EyeSide.LEFT,
                2, 10, 22, null, null, List.of(3, 4));
    }
}
