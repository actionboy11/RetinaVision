package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.PatientProfileMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.PatientProfileEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ClinicalAccessServiceImplTest {

    @Mock private CaseMapper caseMapper;
    @Mock private ImageMapper imageMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private AnalysisResultMapper resultMapper;
    @Mock private PatientProfileMapper patientProfileMapper;

    private ClinicalAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClinicalAccessServiceImpl(caseMapper, imageMapper, taskMapper, resultMapper, patientProfileMapper);
        lenient().when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder()
                .id(10).patientId(100L).createdBy(99).assignedDoctorId(20).build());
        PatientProfileEntity profile = new PatientProfileEntity();
        profile.setId(100L);
        profile.setAccountUserId(7);
        lenient().when(patientProfileMapper.selectById(100L)).thenReturn(profile);
    }

    @Test
    void ownerCanAccessOwnCase() {
        assertThatCode(() -> service.assertCanAccessCase(user(7, UserRole.USER), 10L))
                .doesNotThrowAnyException();
    }

    @Test
    void nonOwnerGetsNotFoundToPreventIdEnumeration() {
        BaseException exception = catchThrowableOfType(
                () -> service.assertCanAccessCase(user(8, UserRole.USER), 10L), BaseException.class);

        assertThat(exception.getCode()).isEqualTo(ErrorMessageSignal.NOT_FOUND);
    }

    @Test
    void assignedDoctorCanAccessCase() {
        assertThatCode(() -> service.assertCanAccessCase(user(20, UserRole.DOCTOR), 10L)).doesNotThrowAnyException();
    }

    @Test
    void otherDoctorGetsNotFoundToPreventCaseEnumeration() {
        BaseException exception = catchThrowableOfType(
                () -> service.assertCanAccessCase(user(21, UserRole.DOCTOR), 10L), BaseException.class);

        assertThat(exception.getCode()).isEqualTo(ErrorMessageSignal.NOT_FOUND);
    }

    @Test
    void researcherCannotAccessClinicalCases() {
        BaseException exception = catchThrowableOfType(
                () -> service.assertCanAccessCase(user(7, UserRole.RESEARCHER), 10L), BaseException.class);
        assertThat(exception.getCode()).isEqualTo(ErrorMessageSignal.FORBIDDEN);
    }

    @Test
    void administratorCannotAccessClinicalCases() {
        BaseException exception = catchThrowableOfType(
                () -> service.assertCanAccessCase(user(1, UserRole.ADMIN), 10L), BaseException.class);

        assertThat(exception.getCode()).isEqualTo(ErrorMessageSignal.FORBIDDEN);
    }

    @Test
    void patientCannotModifySubmittedCase() {
        lenient().when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder()
                .id(10).patientId(100L).assignedDoctorId(20)
                .workflowStatus(CaseWorkflowStatus.SUBMITTED).build());

        BaseException exception = catchThrowableOfType(
                () -> service.assertCanModifyCase(user(7, UserRole.USER), 10L), BaseException.class);

        assertThat(exception.getCode()).isEqualTo(ErrorMessageSignal.CONFLICT);
    }

    private CurrentUserVO user(int id, UserRole role) {
        return new CurrentUserVO(id, "user" + id, "User " + id, role);
    }
}
