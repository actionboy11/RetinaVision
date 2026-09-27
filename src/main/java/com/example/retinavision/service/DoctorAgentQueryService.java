package com.example.retinavision.service;

import com.example.retinavision.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.agent.DoctorTaskSearchCriteria;
import com.example.retinavision.agent.DoctorClinicalQueueCriteria;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.DoctorWorkloadVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskDetailVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskSummaryVO;
import com.example.retinavision.pojo.VO.DoctorClinicalQueueItemVO;
import com.example.retinavision.result.PageResult;

public interface DoctorAgentQueryService {
    DoctorWorkloadVO getClinicalWorkload(CurrentUserVO doctor);

    PageResult<DoctorAgentCaseSummaryVO> searchAssignedCases(DoctorCaseSearchCriteria criteria,
                                                             Integer page,
                                                             Integer pageSize,
                                                             CurrentUserVO doctor);

    PageResult<DoctorAgentTaskSummaryVO> searchTasks(DoctorTaskSearchCriteria criteria,
                                                      Integer page,
                                                      Integer pageSize,
                                                      CurrentUserVO doctor);

    DoctorAgentTaskDetailVO getTaskDetail(String taskReference, CurrentUserVO doctor);

    PageResult<DoctorClinicalQueueItemVO> searchClinicalQueue(DoctorClinicalQueueCriteria criteria,
                                                               Integer page,
                                                               Integer pageSize,
                                                               CurrentUserVO doctor);
}
