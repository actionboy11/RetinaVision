package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.PatientProfileSource;
import com.example.retinavision.enumeration.PatientProfileStatus;
import com.example.retinavision.enumeration.PatientGender;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.DTO.CaseDoctorAssignmentDTO;
import com.example.retinavision.pojo.DTO.CaseInsertDTO;
import com.example.retinavision.pojo.DTO.CaseListQueryDTO;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.PatientProfileEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseServiceImplTest {
    @Mock private CaseMapper caseMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private UserRegisterMapper userMapper;
    @Mock private ImageMapper imageMapper;
    @Mock private com.example.retinavision.service.PatientProfileService patientProfiles;

    private CaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CaseServiceImpl(caseMapper, taskMapper, userMapper, imageMapper, patientProfiles);
    }

    @Test
    void ordinaryUserMustChooseActiveDoctor() {
        CaseInsertDTO request = request(null);

        assertThatThrownBy(() -> service.addCase(request, user(7, UserRole.USER)))
                .isInstanceOf(BaseException.class)
                .extracting("code").isEqualTo(ErrorMessageSignal.PARAM_ERROR);
    }

    @Test
    void ordinaryUserCaseUsesSelectedDoctor() {
        CaseInsertDTO request = request(20);
        when(userMapper.selectById(20)).thenReturn(doctor(20));
        when(patientProfiles.getOrCreateAccountProfile(7)).thenReturn(profile(11L, "PT-7K3M-9Q2D"));
        doAnswer(invocation -> {
            ((CaseEntity) invocation.getArgument(0)).setId(10);
            return 1;
        }).when(caseMapper).insert(any(CaseEntity.class));
        when(caseMapper.getCaseById(10)).thenReturn(new CaseListItemVO());

        service.addCase(request, user(7, UserRole.USER));

        ArgumentCaptor<CaseEntity> captor = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(7);
        assertThat(captor.getValue().getAssignedDoctorId()).isEqualTo(20);
        assertThat(captor.getValue().getPatientId()).isEqualTo(11L);
        assertThat(captor.getValue().getPatientCode()).isEqualTo("PT-7K3M-9Q2D");
        assertThat(captor.getValue().getWorkflowStatus()).isEqualTo(CaseWorkflowStatus.DRAFT);
    }

    @Test
    void doctorCreatedCaseIsAssignedToSelf() {
        CaseInsertDTO request = request(null);
        request.setPatientId(12L);
        when(patientProfiles.requireDoctorAccessible(12L, 20)).thenReturn(profile(12L, "PT-ABCD-EFGH"));
        doAnswer(invocation -> {
            ((CaseEntity) invocation.getArgument(0)).setId(10);
            return 1;
        }).when(caseMapper).insert(any(CaseEntity.class));
        when(caseMapper.getCaseById(10)).thenReturn(new CaseListItemVO());

        service.addCase(request, user(20, UserRole.DOCTOR));

        ArgumentCaptor<CaseEntity> captor = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseMapper).insert(captor.capture());
        assertThat(captor.getValue().getAssignedDoctorId()).isEqualTo(20);
        assertThat(captor.getValue().getPatientId()).isEqualTo(12L);
    }

    @Test
    void existingTasksLockDoctorReassignment() {
        CaseEntity medicalCase = CaseEntity.builder().id(10).patientId(11L).createdBy(7)
                .workflowStatus(CaseWorkflowStatus.DRAFT).assignedDoctorId(20).build();
        when(caseMapper.selectById(10)).thenReturn(medicalCase);
        when(patientProfiles.getOrCreateAccountProfile(7)).thenReturn(profile(11L, "PT-7K3M-9Q2D"));
        when(userMapper.selectById(21)).thenReturn(doctor(21));
        when(taskMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.assignDoctor(10, new CaseDoctorAssignmentDTO(21),
                user(7, UserRole.USER)))
                .isInstanceOf(BaseException.class)
                .extracting("code").isEqualTo(ErrorMessageSignal.CONFLICT);
    }

    @Test
    void patientCannotSubmitDraftWithoutImage() {
        CaseEntity medicalCase = CaseEntity.builder().id(10).patientId(11L)
                .workflowStatus(CaseWorkflowStatus.DRAFT).assignedDoctorId(20).build();
        when(caseMapper.selectById(10)).thenReturn(medicalCase);
        when(patientProfiles.getOrCreateAccountProfile(7)).thenReturn(profile(11L, "PT-7K3M-9Q2D"));
        when(imageMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.submit(10, user(7, UserRole.USER)))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("图像");
    }

    @Test
    void patientSubmitsDraftWithImage() {
        CaseEntity medicalCase = CaseEntity.builder().id(10).patientId(11L)
                .workflowStatus(CaseWorkflowStatus.DRAFT).assignedDoctorId(20).build();
        when(caseMapper.selectById(10)).thenReturn(medicalCase);
        when(patientProfiles.getOrCreateAccountProfile(7)).thenReturn(profile(11L, "PT-7K3M-9Q2D"));
        when(imageMapper.selectCount(any())).thenReturn(1L);
        when(caseMapper.getCaseById(10)).thenReturn(new CaseListItemVO());

        service.submit(10, user(7, UserRole.USER));

        assertThat(medicalCase.getWorkflowStatus()).isEqualTo(CaseWorkflowStatus.SUBMITTED);
        verify(caseMapper).updateById(medicalCase);
    }

    @Test
    void patientCannotWithdrawAfterVesselTaskExists() {
        CaseEntity medicalCase = CaseEntity.builder().id(10).patientId(11L)
                .workflowStatus(CaseWorkflowStatus.SUBMITTED).assignedDoctorId(20).build();
        when(caseMapper.selectById(10)).thenReturn(medicalCase);
        when(patientProfiles.getOrCreateAccountProfile(7)).thenReturn(profile(11L, "PT-7K3M-9Q2D"));
        when(taskMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.withdraw(10, user(7, UserRole.USER)))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("分析");
    }

    @Test
    void patientCaseListDoesNotExposeDiagnosisNote() {
        CaseListItemVO row = CaseListItemVO.builder().id(10).diagnosisNote("医生未签发意见").build();
        when(caseMapper.countCasePage(any(), anyInt(), isNull())).thenReturn(1L);
        when(caseMapper.selectCasePage(any(), anyInt(), isNull(), anyInt(), anyInt()))
                .thenReturn(java.util.List.of(row));

        var page = service.getCaseList(new CaseListQueryDTO(), user(7, UserRole.USER));

        assertThat(page.getRecords().get(0).getDiagnosisNote()).isNull();
    }

    private CaseInsertDTO request(Integer doctorId) {
        return new CaseInsertDTO(null, 50, PatientGender.UNKNOWN, EyeSide.LEFT, null, doctorId);
    }

    private CurrentUserVO user(int id, UserRole role) {
        return new CurrentUserVO(id, "u" + id, "User " + id, role);
    }

    private UserEntity doctor(int id) {
        UserEntity doctor = new UserEntity();
        doctor.setId(id);
        doctor.setRoleCode(UserRole.DOCTOR);
        doctor.setStatus(1);
        return doctor;
    }

    private PatientProfileEntity profile(long id, String number) {
        PatientProfileEntity profile = new PatientProfileEntity();
        profile.setId(id);
        profile.setPatientNo(number);
        profile.setSource(PatientProfileSource.ACCOUNT);
        profile.setStatus(PatientProfileStatus.ACTIVE);
        return profile;
    }
}
