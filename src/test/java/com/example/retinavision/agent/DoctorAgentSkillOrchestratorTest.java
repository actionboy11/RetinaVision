package com.example.retinavision.agent;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorWorkloadVO;
import com.example.retinavision.service.AgentClinicalReferenceService;
import com.example.retinavision.service.CaseAnalysisTimelineService;
import com.example.retinavision.service.DoctorAgentQueryService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoctorAgentSkillOrchestratorTest {

    @Test
    void workloadQuestionReturnsTrustedStructuredMetrics() {
        DoctorAgentQueryService queries = mock(DoctorAgentQueryService.class);
        AgentQueryContextService contexts = mock(AgentQueryContextService.class);
        CurrentUserVO doctor = doctor();
        when(queries.getClinicalWorkload(doctor)).thenReturn(new DoctorWorkloadVO(8, 11, 3, 4, 2, 1));
        AgentSkillVersionBindingService versions = mock(AgentSkillVersionBindingService.class);
        when(versions.resolve(9L, AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW))
                .thenReturn(new AgentSkillRuntimeVersion(101L, 3));
        DoctorAgentSkillOrchestrator orchestrator = new DoctorAgentSkillOrchestrator(
                new DefaultAgentSkillRouter(), queries, contexts,
                mock(AgentClinicalReferenceService.class), mock(CaseAnalysisTimelineService.class), versions);

        Optional<DoctorAgentSkillResult> result = orchestrator.handle(9L, "我有多少名患者？", doctor);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().skillCode()).isEqualTo(AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW);
        assertThat(result.orElseThrow().data().type()).isEqualTo("METRICS");
        assertThat(result.orElseThrow().skillVersion()).isEqualTo(3);
        assertThat(result.orElseThrow().answer()).contains("8", "11");
    }

    private CurrentUserVO doctor() {
        CurrentUserVO user = new CurrentUserVO();
        user.setId(27);
        user.setRoleCode(UserRole.DOCTOR);
        return user;
    }
}
