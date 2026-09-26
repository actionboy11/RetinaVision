package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.pojo.DTO.UpdateUserRoleDTO;
import com.example.retinavision.pojo.Entity.UserEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class UserAdministrationServiceImplTest {
    private final UserRegisterMapper mapper = mock(UserRegisterMapper.class);
    private final CaseMapper caseMapper = mock(CaseMapper.class);
    private final UserAdministrationServiceImpl service = new UserAdministrationServiceImpl(mapper, caseMapper);

    @Test
    void doctorRoleRequiresProfessionalNumber() {
        UserEntity user = new UserEntity();
        user.setId(10);
        when(mapper.selectById(10)).thenReturn(user);

        assertThatThrownBy(() -> service.assignRole(10,
                new UpdateUserRoleDTO(UserRole.DOCTOR, null), 1))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void adminAssignmentPersistsAuditFields() {
        UserEntity user = new UserEntity();
        user.setId(10);
        when(mapper.selectById(10)).thenReturn(user);

        service.assignRole(10, new UpdateUserRoleDTO(UserRole.DOCTOR, "DOC-001"), 1);

        assertThat(user.getRoleCode()).isEqualTo(UserRole.DOCTOR);
        assertThat(user.getProfessionalNo()).isEqualTo("DOC-001");
        assertThat(user.getRoleAssignedBy()).isEqualTo(1);
        assertThat(user.getRoleAssignedAt()).isNotNull();
        verify(mapper).updateById(user);
    }

    @Test
    void doctorWithAssignedCasesCannotBeDemoted() {
        UserEntity doctor = new UserEntity();
        doctor.setId(10);
        doctor.setRoleCode(UserRole.DOCTOR);
        when(mapper.selectById(10)).thenReturn(doctor);
        when(caseMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.assignRole(10,
                new UpdateUserRoleDTO(UserRole.USER, null), 1))
                .isInstanceOf(BaseException.class);
    }
}
