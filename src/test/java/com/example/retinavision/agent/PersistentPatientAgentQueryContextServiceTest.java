package com.example.retinavision.agent;

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

class PersistentPatientAgentQueryContextServiceTest {

    @Test
    void savesPatientFiltersSelectionAndAtMostTenReferences() throws Exception {
        AgentQueryContextMapper mapper = mock(AgentQueryContextMapper.class);
        when(mapper.selectById(8L)).thenReturn(null);
        var service = new PersistentPatientAgentQueryContextService(mapper, new ObjectMapper());
        var references = java.util.stream.LongStream.rangeClosed(1, 11)
                .mapToObj(id -> new PatientAgentReference(id, "C-" + id, id + 100, 2)).toList();

        service.save(8L, new PatientAgentQueryContextSnapshot(
                AgentSkillCode.MY_SIGNED_REPORT, true, true, 2, 10, 18,
                7L, 107L, 2, references));

        ArgumentCaptor<AgentQueryContextEntity> saved = ArgumentCaptor.forClass(AgentQueryContextEntity.class);
        verify(mapper).insert(saved.capture());
        assertThat(saved.getValue().getCurrentFiltersJson())
                .contains("\"reuploadOnly\":true", "\"signedReportOnly\":true",
                        "\"selectedResultId\":107", "\"selectedReportVersion\":2");
        assertThat(saved.getValue().getSelectedCaseId()).isEqualTo(7);
        assertThat(saved.getValue().getPageSize()).isEqualTo(10);
        assertThat(new ObjectMapper().readTree(saved.getValue().getRecentResultReferencesJson())).hasSize(10);
    }

    @Test
    void restoresPatientContextAndRejectsExpiredOrDoctorShapedContext() {
        AgentQueryContextMapper mapper = mock(AgentQueryContextMapper.class);
        AgentQueryContextEntity entity = entity(8L);
        entity.setCurrentSkillCode("MY_SIGNED_REPORT");
        entity.setReferenceType("REPORT");
        entity.setCurrentFiltersJson("{\"reuploadOnly\":false,\"signedReportOnly\":true," +
                "\"selectedResultId\":107,\"selectedReportVersion\":2}");
        entity.setSelectedCaseId(7);
        entity.setRecentResultReferencesJson(
                "[{\"caseId\":7,\"caseNo\":\"C-7\",\"resultId\":107,\"reportVersion\":2}]");
        when(mapper.selectById(8L)).thenReturn(entity);
        AgentQueryContextEntity expired = entity(9L);
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(mapper.selectById(9L)).thenReturn(expired);
        AgentQueryContextEntity doctor = entity(10L);
        doctor.setCurrentSkillCode("ASSIGNED_CASE_SEARCH");
        doctor.setRecentResultReferencesJson("[1,2]");
        when(mapper.selectById(10L)).thenReturn(doctor);
        var service = new PersistentPatientAgentQueryContextService(mapper, new ObjectMapper());

        var restored = service.load(8L).orElseThrow();

        assertThat(restored.currentSkill()).isEqualTo(AgentSkillCode.MY_SIGNED_REPORT);
        assertThat(restored.selectedResultId()).isEqualTo(107L);
        assertThat(restored.references()).singleElement().satisfies(reference ->
                assertThat(reference.caseNo()).isEqualTo("C-7"));
        assertThat(service.load(9L)).isEmpty();
        assertThat(service.load(10L)).isEmpty();
    }

    @Test
    void restoresCaseListContextWithoutSelectedCase() {
        AgentQueryContextMapper mapper = mock(AgentQueryContextMapper.class);
        AgentQueryContextEntity entity = entity(11L);
        entity.setTotal(12L);
        entity.setRecentResultReferencesJson(
                "[{\"caseId\":69,\"caseNo\":\"C-69\",\"resultId\":null,\"reportVersion\":null}]");
        when(mapper.selectById(11L)).thenReturn(entity);
        var service = new PersistentPatientAgentQueryContextService(mapper, new ObjectMapper());

        var restored = service.load(11L).orElseThrow();

        assertThat(restored.selectedCaseId()).isNull();
        assertThat(restored.total()).isEqualTo(12);
        assertThat(restored.references()).singleElement().satisfies(reference ->
                assertThat(reference.caseId()).isEqualTo(69L));
    }

    private AgentQueryContextEntity entity(long sessionId) {
        AgentQueryContextEntity entity = new AgentQueryContextEntity();
        entity.setSessionId(sessionId);
        entity.setCurrentSkillCode("MY_CASE_LIST");
        entity.setReferenceType("CASE");
        entity.setCurrentFiltersJson("{}");
        entity.setCurrentPage(1);
        entity.setPageSize(10);
        entity.setTotal(0L);
        entity.setRecentResultReferencesJson("[]");
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return entity;
    }
}
