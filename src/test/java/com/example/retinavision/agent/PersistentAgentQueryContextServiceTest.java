package com.example.retinavision.agent;

import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
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
        assertThat(restored.referenceType()).isEqualTo(AgentReferenceType.CASE);
        assertThat(restored.recentReferenceIds()).containsExactly(3L, 4L);
    }

    @Test
    void savesAndRestoresTypedTaskContextWithAtMostTenReferences() throws Exception {
        AgentQueryContextMapper mapper = mock(AgentQueryContextMapper.class);
        when(mapper.selectById(21L)).thenReturn(null);
        PersistentAgentQueryContextService service = new PersistentAgentQueryContextService(mapper, new ObjectMapper());
        AgentQueryContextSnapshot context = new AgentQueryContextSnapshot(
                AgentSkillCode.DOCTOR_TASK_SEARCH, AgentReferenceType.TASK,
                SegmentationState.ANY, DoctorClinicalState.ANY,
                AnalysisTaskType.IMAGE_QUALITY_CHECK, DoctorTaskStatusFilter.FAILED, null,
                DoctorDateWindow.LAST_7_DAYS, null, 2, 10, 25,
                null, 91L, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L));

        service.save(21L, context);

        ArgumentCaptor<AgentQueryContextEntity> saved = ArgumentCaptor.forClass(AgentQueryContextEntity.class);
        verify(mapper).insert(saved.capture());
        assertThat(saved.getValue().getReferenceType()).isEqualTo("TASK");
        assertThat(saved.getValue().getCurrentFiltersJson())
                .contains("\"taskType\":\"IMAGE_QUALITY_CHECK\"")
                .contains("\"taskStatus\":\"FAILED\"");
        assertThat(new ObjectMapper().readValue(saved.getValue().getRecentResultReferencesJson(), Long[].class))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    void restoresClinicalQueueFiltersAndLongReferences() {
        AgentQueryContextMapper mapper = mock(AgentQueryContextMapper.class);
        AgentQueryContextEntity entity = baseEntity(31L);
        entity.setCurrentSkillCode(AgentSkillCode.DOCTOR_CLINICAL_QUEUE.name());
        entity.setReferenceType("CLINICAL_QUEUE");
        entity.setCurrentFiltersJson("{\"queueType\":\"PENDING_REPORT\",\"dateWindow\":\"TODAY\"}");
        entity.setRecentResultReferencesJson("[2147483648,9]");
        when(mapper.selectById(31L)).thenReturn(entity);
        PersistentAgentQueryContextService service = new PersistentAgentQueryContextService(mapper, new ObjectMapper());

        AgentQueryContextSnapshot restored = service.load(31L).orElseThrow();

        assertThat(restored.referenceType()).isEqualTo(AgentReferenceType.CLINICAL_QUEUE);
        assertThat(restored.queueType()).isEqualTo(DoctorClinicalQueueType.PENDING_REPORT);
        assertThat(restored.dateWindow()).isEqualTo(DoctorDateWindow.TODAY);
        assertThat(restored.recentReferenceIds()).containsExactly(2147483648L, 9L);
    }

    @Test
    void expiredOrMalformedContextReturnsEmpty() {
        AgentQueryContextMapper mapper = mock(AgentQueryContextMapper.class);
        AgentQueryContextEntity expired = baseEntity(41L);
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(mapper.selectById(41L)).thenReturn(expired);
        AgentQueryContextEntity malformed = baseEntity(42L);
        malformed.setRecentResultReferencesJson("not-json");
        when(mapper.selectById(42L)).thenReturn(malformed);
        PersistentAgentQueryContextService service = new PersistentAgentQueryContextService(mapper, new ObjectMapper());

        assertThat(service.load(41L)).isEmpty();
        assertThat(service.load(42L)).isEmpty();
    }

    private AgentQueryContextSnapshot context() {
        return new AgentQueryContextSnapshot(AgentSkillCode.ASSIGNED_CASE_SEARCH, SegmentationState.FAILED,
                DoctorClinicalState.PENDING_REVIEW, DoctorDateWindow.LAST_30_DAYS, EyeSide.LEFT,
                2, 10, 22, null, null, List.of(3, 4));
    }

    private AgentQueryContextEntity baseEntity(long sessionId) {
        AgentQueryContextEntity entity = new AgentQueryContextEntity();
        entity.setSessionId(sessionId);
        entity.setCurrentSkillCode(AgentSkillCode.ASSIGNED_CASE_SEARCH.name());
        entity.setCurrentFiltersJson("{}");
        entity.setCurrentPage(1);
        entity.setPageSize(10);
        entity.setTotal(0L);
        entity.setRecentResultReferencesJson("[]");
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return entity;
    }
}
