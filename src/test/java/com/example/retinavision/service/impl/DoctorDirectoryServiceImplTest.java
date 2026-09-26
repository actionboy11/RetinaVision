package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.Entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoctorDirectoryServiceImplTest {
    private final UserRegisterMapper mapper = mock(UserRegisterMapper.class);
    private final DoctorDirectoryServiceImpl service = new DoctorDirectoryServiceImpl(mapper);

    @Test
    void exposesOnlySafeDoctorSelectionFields() {
        UserEntity doctor = new UserEntity();
        doctor.setId(20);
        doctor.setRealName("王医生");
        doctor.setProfessionalNo("DOC-020");
        doctor.setRoleCode(UserRole.DOCTOR);
        doctor.setStatus(1);
        when(mapper.selectList(any())).thenReturn(List.of(doctor));

        var result = service.listActiveDoctors();

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(20);
            assertThat(item.displayName()).isEqualTo("王医生");
            assertThat(item.professionalNo()).isEqualTo("DOC-020");
        });
    }
}
