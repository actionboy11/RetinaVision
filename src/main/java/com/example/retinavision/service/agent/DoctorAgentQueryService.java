package com.example.retinavision.service.agent;

import com.example.retinavision.pojo.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.pojo.agent.DoctorClinicalQueueCriteria;
import com.example.retinavision.pojo.agent.DoctorTaskSearchCriteria;

import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
import com.example.retinavision.pojo.cases.vo.DoctorAgentCaseSummaryVO;
import com.example.retinavision.pojo.cases.vo.DoctorWorkloadVO;
import com.example.retinavision.pojo.analysis.vo.DoctorAgentTaskDetailVO;
import com.example.retinavision.pojo.analysis.vo.DoctorAgentTaskSummaryVO;
import com.example.retinavision.pojo.clinical.vo.DoctorClinicalQueueItemVO;
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
