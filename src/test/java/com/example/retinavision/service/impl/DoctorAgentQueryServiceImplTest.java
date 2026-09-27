package com.example.retinavision.service.impl;

import com.example.retinavision.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.agent.DoctorDateWindow;
import com.example.retinavision.agent.DoctorTaskSearchCriteria;
import com.example.retinavision.agent.DoctorTaskStatusFilter;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.agent.SegmentationState;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.DoctorAgentQueryMapper;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskDetailVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskLogVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskSummaryVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorAgentQueryServiceImplTest {

    @Test
    void searchClampsPageSizeAndScopesQueryToCurrentDoctor() {
        DoctorAgentQueryMapper mapper = mock(DoctorAgentQueryMapper.class);
        DoctorAgentQueryServiceImpl service = new DoctorAgentQueryServiceImpl(mapper);
        CurrentUserVO doctor = new CurrentUserVO();
        doctor.setId(27);
        doctor.setRoleCode(UserRole.DOCTOR);
        DoctorCaseSearchCriteria criteria = new DoctorCaseSearchCriteria(SegmentationState.NOT_CREATED, null);
        when(mapper.countAssignedCases(27, criteria)).thenReturn(1L);
        when(mapper.selectAssignedCases(27, criteria, 0, 10)).thenReturn(List.of(new DoctorAgentCaseSummaryVO()));

        var result = service.searchAssignedCases(criteria, 0, 50, doctor);

        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(1);
        verify(mapper).countAssignedCases(27, criteria);
        verify(mapper).selectAssignedCases(27, criteria, 0, 10);
    }

    @Test
    void workloadUsesDistinctPatientAggregationReturnedByDoctorScopedMapper() {
        DoctorAgentQueryMapper mapper = mock(DoctorAgentQueryMapper.class);
        DoctorAgentQueryServiceImpl service = new DoctorAgentQueryServiceImpl(mapper);
        CurrentUserVO doctor = new CurrentUserVO();
        doctor.setId(27);
        doctor.setRoleCode(UserRole.DOCTOR);
        var expected = new com.example.retinavision.pojo.VO.DoctorWorkloadVO(4, 7, 2, 3, 1, 1);
        when(mapper.selectWorkload(27)).thenReturn(expected);

        assertThat(service.getClinicalWorkload(doctor)).isEqualTo(expected);
        verify(mapper).selectWorkload(27);
    }

    @Test
    void taskSearchNormalizesDefaultsAndForwardsCurrentDoctor() {
        DoctorAgentQueryMapper mapper = mock(DoctorAgentQueryMapper.class);
        DoctorAgentQueryServiceImpl service = new DoctorAgentQueryServiceImpl(mapper);
        CurrentUserVO doctor = doctor(27);
        when(mapper.countTasks(eq(27), any())).thenReturn(1L);
        when(mapper.selectTasks(eq(27), any(), eq(0), eq(10))).thenReturn(List.of(new DoctorAgentTaskSummaryVO()));

        var result = service.searchTasks(null, 0, 50, doctor);

        ArgumentCaptor<DoctorTaskSearchCriteria> criteria = ArgumentCaptor.forClass(DoctorTaskSearchCriteria.class);
        verify(mapper).countTasks(eq(27), criteria.capture());
        verify(mapper).selectTasks(eq(27), eq(criteria.getValue()), eq(0), eq(10));
        assertThat(criteria.getValue().taskType()).isEqualTo(AnalysisTaskType.VESSEL_SEGMENTATION);
        assertThat(criteria.getValue().status()).isEqualTo(DoctorTaskStatusFilter.ANY);
        assertThat(criteria.getValue().dateWindow()).isEqualTo(DoctorDateWindow.ANY);
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
    }

    @Test
    void taskDetailUsesExactReferenceWithinDoctorScopeAndSanitizesMessages() {
        DoctorAgentQueryMapper mapper = mock(DoctorAgentQueryMapper.class);
        DoctorAgentQueryServiceImpl service = new DoctorAgentQueryServiceImpl(mapper);
        DoctorAgentTaskDetailVO detail = new DoctorAgentTaskDetailVO();
        detail.setTaskId(81L);
        detail.setErrorSummary("first line\r\nsecond line " + "x".repeat(300));
        when(mapper.selectTaskDetail(27, "T202609270001", null)).thenReturn(detail);
        DoctorAgentTaskLogVO log = new DoctorAgentTaskLogVO();
        log.setMessage("retry\njava.lang.IllegalStateException: secret " + "y".repeat(300));
        log.setCreatedAt(LocalDateTime.now());
        when(mapper.selectTaskLogs(27, 81L)).thenReturn(List.of(log));

        DoctorAgentTaskDetailVO result = service.getTaskDetail("T202609270001", doctor(27));

        verify(mapper).selectTaskDetail(27, "T202609270001", null);
        verify(mapper).selectTaskLogs(27, 81L);
        assertThat(result.getErrorSummary()).doesNotContain("\n", "\r").hasSizeLessThanOrEqualTo(200);
        assertThat(result.getLogs()).singleElement().extracting(DoctorAgentTaskLogVO::getMessage)
                .asString().doesNotContain("\n", "\r").hasSizeLessThanOrEqualTo(200);
    }

    @Test
    void numericTaskReferenceUsesIdAlternativeInSameDoctorScopedLookup() {
        DoctorAgentQueryMapper mapper = mock(DoctorAgentQueryMapper.class);
        DoctorAgentQueryServiceImpl service = new DoctorAgentQueryServiceImpl(mapper);
        DoctorAgentTaskDetailVO detail = new DoctorAgentTaskDetailVO();
        detail.setTaskId(81L);
        when(mapper.selectTaskDetail(27, "81", 81L)).thenReturn(detail);
        when(mapper.selectTaskLogs(27, 81L)).thenReturn(List.of());

        service.getTaskDetail("81", doctor(27));

        verify(mapper).selectTaskDetail(27, "81", 81L);
    }

    @Test
    void taskDetailHidesWhetherUnknownOrOwnedByAnotherDoctor() {
        DoctorAgentQueryMapper mapper = mock(DoctorAgentQueryMapper.class);
        DoctorAgentQueryServiceImpl service = new DoctorAgentQueryServiceImpl(mapper);
        when(mapper.selectTaskDetail(27, "T-OTHER", null)).thenReturn(null);

        assertThatThrownBy(() -> service.getTaskDetail("T-OTHER", doctor(27)))
                .isInstanceOf(BaseException.class)
                .hasMessage("资源不存在");
        verify(mapper, never()).selectTaskLogs(eq(27), any());
    }

    @Test
    void taskQueriesRequireDoctorRole() {
        DoctorAgentQueryMapper mapper = mock(DoctorAgentQueryMapper.class);
        DoctorAgentQueryServiceImpl service = new DoctorAgentQueryServiceImpl(mapper);
        CurrentUserVO patient = new CurrentUserVO();
        patient.setId(5);
        patient.setRoleCode(UserRole.USER);

        assertThatThrownBy(() -> service.searchTasks(null, 1, 10, patient))
                .isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.getTaskDetail("T1", patient))
                .isInstanceOf(BaseException.class);
    }

    private CurrentUserVO doctor(int id) {
        CurrentUserVO doctor = new CurrentUserVO();
        doctor.setId(id);
        doctor.setRoleCode(UserRole.DOCTOR);
        return doctor;
    }
}
