package com.example.retinavision.mapper;

import com.example.retinavision.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
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
}
