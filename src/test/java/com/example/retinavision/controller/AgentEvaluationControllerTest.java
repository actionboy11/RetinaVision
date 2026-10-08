package com.example.retinavision.controller;

import com.example.retinavision.agent.evaluation.AgentEvaluationAdministrationService;
import com.example.retinavision.agent.evaluation.AgentEvaluationService;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.DTO.ReviewAgentEvaluationDTO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AgentEvaluationControllerTest {

    @Test
    void rejectsDoctorFromEveryEvaluationManagementEntryPoint() {
        AgentEvaluationAdministrationService administration = mock(AgentEvaluationAdministrationService.class);
        AgentEvaluationController controller = new AgentEvaluationController(
                mock(AgentEvaluationService.class), administration);
        var doctor = auth(UserRole.DOCTOR);

        assertThatThrownBy(() -> controller.options(doctor)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> controller.datasets(doctor)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> controller.run(1L, doctor)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> controller.cancel(1L, doctor)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> controller.review(1L,
                new ReviewAgentEvaluationDTO("APPROVED", "ok"), doctor)).isInstanceOf(BaseException.class);
        verifyNoInteractions(administration);
    }

    @Test
    void adminCanReadDatasetsAndReviewCompletedRun() {
        AgentEvaluationAdministrationService administration = mock(AgentEvaluationAdministrationService.class);
        when(administration.listDatasets()).thenReturn(List.of());
        AgentEvaluationController controller = new AgentEvaluationController(
                mock(AgentEvaluationService.class), administration);

        controller.datasets(auth(UserRole.ADMIN));
        controller.review(9L, new ReviewAgentEvaluationDTO("APPROVED", "抽查通过"), auth(UserRole.ADMIN));

        verify(administration).listDatasets();
        verify(administration).review(9L, "APPROVED", "抽查通过", 1);
    }

    private UsernamePasswordAuthenticationToken auth(UserRole role) {
        return new UsernamePasswordAuthenticationToken(
                new CurrentUserVO(1, "tester", "测试", role), null, List.of());
    }
}
