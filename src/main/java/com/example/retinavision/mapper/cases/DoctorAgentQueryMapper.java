package com.example.retinavision.mapper.cases;

import com.example.retinavision.pojo.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.pojo.agent.DoctorClinicalQueueCriteria;
import com.example.retinavision.pojo.agent.DoctorTaskSearchCriteria;

import com.example.retinavision.pojo.cases.vo.DoctorAgentCaseSummaryVO;
import com.example.retinavision.pojo.analysis.vo.DoctorAgentTaskDetailVO;
import com.example.retinavision.pojo.analysis.vo.DoctorAgentTaskLogVO;
import com.example.retinavision.pojo.analysis.vo.DoctorAgentTaskSummaryVO;
import com.example.retinavision.pojo.clinical.vo.DoctorClinicalQueueItemVO;
import com.example.retinavision.pojo.cases.vo.DoctorWorkloadVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DoctorAgentQueryMapper {
    DoctorWorkloadVO selectWorkload(@Param("doctorId") Integer doctorId);

    long countAssignedCases(@Param("doctorId") Integer doctorId,
                            @Param("criteria") DoctorCaseSearchCriteria criteria);

    List<DoctorAgentCaseSummaryVO> selectAssignedCases(@Param("doctorId") Integer doctorId,
                                                       @Param("criteria") DoctorCaseSearchCriteria criteria,
                                                       @Param("offset") int offset,
                                                       @Param("pageSize") int pageSize);

    long countTasks(@Param("doctorId") Integer doctorId,
                    @Param("criteria") DoctorTaskSearchCriteria criteria);

    List<DoctorAgentTaskSummaryVO> selectTasks(@Param("doctorId") Integer doctorId,
                                               @Param("criteria") DoctorTaskSearchCriteria criteria,
                                               @Param("offset") int offset,
                                               @Param("pageSize") int pageSize);

    DoctorAgentTaskDetailVO selectTaskDetail(@Param("doctorId") Integer doctorId,
                                             @Param("taskReference") String taskReference,
                                             @Param("taskId") Long taskId);

    List<DoctorAgentTaskLogVO> selectTaskLogs(@Param("doctorId") Integer doctorId,
                                              @Param("taskId") Long taskId);

    long countClinicalQueue(@Param("doctorId") Integer doctorId,
                            @Param("criteria") DoctorClinicalQueueCriteria criteria);

    List<DoctorClinicalQueueItemVO> selectClinicalQueue(@Param("doctorId") Integer doctorId,
                                                        @Param("criteria") DoctorClinicalQueueCriteria criteria,
                                                        @Param("offset") int offset,
                                                        @Param("pageSize") int pageSize);
}
