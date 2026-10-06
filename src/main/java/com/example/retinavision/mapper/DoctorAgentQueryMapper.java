package com.example.retinavision.mapper;

import com.example.retinavision.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.agent.DoctorTaskSearchCriteria;
import com.example.retinavision.agent.DoctorClinicalQueueCriteria;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskDetailVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskLogVO;
import com.example.retinavision.pojo.VO.DoctorAgentTaskSummaryVO;
import com.example.retinavision.pojo.VO.DoctorClinicalQueueItemVO;
import com.example.retinavision.pojo.VO.DoctorWorkloadVO;
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
