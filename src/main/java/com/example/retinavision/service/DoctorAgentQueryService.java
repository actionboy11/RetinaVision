package com.example.retinavision.service;

import com.example.retinavision.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.DoctorWorkloadVO;
import com.example.retinavision.result.PageResult;

public interface DoctorAgentQueryService {
    DoctorWorkloadVO getClinicalWorkload(CurrentUserVO doctor);

    PageResult<DoctorAgentCaseSummaryVO> searchAssignedCases(DoctorCaseSearchCriteria criteria,
                                                             Integer page,
                                                             Integer pageSize,
                                                             CurrentUserVO doctor);
}
