package com.example.retinavision.service.impl;

import com.example.retinavision.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.agent.SegmentationState;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.mapper.DoctorAgentQueryMapper;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
}
