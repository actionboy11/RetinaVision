package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentClinicalReferenceServiceImplTest {
    private final CaseMapper cases = mock(CaseMapper.class);
    private final TaskMapper tasks = mock(TaskMapper.class);
    private final AgentClinicalReferenceServiceImpl service = new AgentClinicalReferenceServiceImpl(cases, tasks);
    private final CurrentUserVO doctor = new CurrentUserVO(17, "doctor", "医生", UserRole.DOCTOR);

    @Test
    void resolvesCaseReferenceInsideAssignedDoctorScope() {
        CaseListItemVO expected = CaseListItemVO.builder().id(8).caseNo("CASE-8").build();
        when(cases.findAssignedCaseByReference("P0008", null, 17)).thenReturn(expected);

        assertThat(service.resolveCase("P0008", doctor)).isSameAs(expected);
        verify(cases).findAssignedCaseByReference("P0008", null, 17);
    }

    @Test
    void resolvesNumericTaskIdInsideAssignedDoctorScope() {
        TaskEntity expected = TaskEntity.builder().id(29L).taskNo("TASK-29").build();
        when(tasks.findAssignedTaskByReference("29", 29L, 17)).thenReturn(expected);

        assertThat(service.resolveTask("29", doctor)).isSameAs(expected);
    }

    @Test
    void hidesMissingOrOtherDoctorResourceAsNotFound() {
        assertThatThrownBy(() -> service.resolveCase("OTHER-CASE", doctor))
                .isInstanceOf(BaseException.class)
                .hasMessage("资源不存在");
    }

    @Test
    void rejectsAdminFromClinicalResolver() {
        CurrentUserVO admin = new CurrentUserVO(1, "admin", "管理员", UserRole.ADMIN);

        assertThatThrownBy(() -> service.resolveTask("TASK-1", admin))
                .isInstanceOf(BaseException.class);
    }
}
