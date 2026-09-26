package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.PatientProfileSource;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.PatientProfileMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.Entity.PatientProfileEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.pojo.DTO.PatientAccountLinkDTO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientProfileServiceImplTest {
    @Mock private PatientProfileMapper profiles;
    @Mock private UserRegisterMapper users;
    @Mock private CaseMapper cases;
    @Mock private SecurePatientNumberGenerator numbers;

    private PatientProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PatientProfileServiceImpl(profiles, users, cases, numbers);
    }

    @Test
    void createsAccountProfileWithGeneratedNumber() {
        when(numbers.next()).thenReturn("PT-7K3M-9Q2D");
        when(profiles.selectByAccountUserId(7)).thenReturn(null);
        when(profiles.selectByPatientNo("PT-7K3M-9Q2D")).thenReturn(null);
        doAnswer(invocation -> {
            ((PatientProfileEntity) invocation.getArgument(0)).setId(11L);
            return 1;
        }).when(profiles).insert(any(PatientProfileEntity.class));

        PatientProfileEntity created = service.getOrCreateAccountProfile(7);

        assertThat(created.getPatientNo()).isEqualTo("PT-7K3M-9Q2D");
        assertThat(created.getAccountUserId()).isEqualTo(7);
        assertThat(created.getSource()).isEqualTo(PatientProfileSource.ACCOUNT);
        verify(profiles).insert(created);
    }

    @Test
    void onlyDoctorsCanCreateOfflinePatients() {
        assertThatThrownBy(() -> service.createOffline(new CurrentUserVO(
                7, "patient", "Patient", UserRole.USER)))
                .hasMessageContaining("医生");
    }

    @Test
    void linksLegacyProfileAfterRemovingEmptyGeneratedAccountProfile() {
        PatientProfileEntity legacy = profile(20L, PatientProfileSource.LEGACY, null);
        PatientProfileEntity emptyAccount = profile(21L, PatientProfileSource.ACCOUNT, 7);
        when(profiles.selectById(20L)).thenReturn(legacy);
        when(users.selectById(7)).thenReturn(patientAccount(7));
        when(profiles.selectByAccountUserId(7)).thenReturn(emptyAccount);
        when(cases.selectCount(any())).thenReturn(0L);

        service.linkLegacy(20L, new PatientAccountLinkDTO(7), admin());

        verify(profiles).deleteById(21L);
        assertThat(legacy.getAccountUserId()).isEqualTo(7);
        verify(profiles).updateById(legacy);
    }

    @Test
    void refusesToMergeAccountProfileThatAlreadyHasCases() {
        PatientProfileEntity legacy = profile(20L, PatientProfileSource.LEGACY, null);
        when(profiles.selectById(20L)).thenReturn(legacy);
        when(users.selectById(7)).thenReturn(patientAccount(7));
        when(profiles.selectByAccountUserId(7)).thenReturn(profile(21L, PatientProfileSource.ACCOUNT, 7));
        when(cases.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.linkLegacy(20L, new PatientAccountLinkDTO(7), admin()))
                .hasMessageContaining("不能自动合并");
    }

    private PatientProfileEntity profile(long id, PatientProfileSource source, Integer accountUserId) {
        PatientProfileEntity profile = new PatientProfileEntity();
        profile.setId(id);
        profile.setPatientNo("PT-ABCD-" + id);
        profile.setSource(source);
        profile.setAccountUserId(accountUserId);
        return profile;
    }

    private UserEntity patientAccount(int id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setRoleCode(UserRole.USER);
        user.setStatus(1);
        return user;
    }

    private CurrentUserVO admin() {
        return new CurrentUserVO(1, "admin", "Admin", UserRole.ADMIN);
    }
}
