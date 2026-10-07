package com.example.retinavision.mapper;

import com.example.retinavision.agent.PatientCaseSearchCriteria;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;
import com.example.retinavision.pojo.VO.PatientAgentSignedReportVO;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PatientAgentQueryMapper {
    long countMyCases(@Param("userId") Integer userId,
                      @Param("criteria") PatientCaseSearchCriteria criteria);

    List<CaseProjection> selectMyCases(@Param("userId") Integer userId,
                                       @Param("criteria") PatientCaseSearchCriteria criteria,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    ProgressProjection selectMyCaseProgress(@Param("userId") Integer userId,
                                             @Param("caseReference") String caseReference,
                                             @Param("caseId") Long caseId);

    long countMySignedReports(@Param("userId") Integer userId,
                              @Param("caseReference") String caseReference,
                              @Param("caseId") Long caseId);

    List<PatientAgentSignedReportVO> selectMySignedReports(@Param("userId") Integer userId,
                                                           @Param("caseReference") String caseReference,
                                                           @Param("caseId") Long caseId,
                                                           @Param("offset") int offset,
                                                           @Param("pageSize") int pageSize);

    PatientAgentReportDetailVO selectMySignedReport(@Param("userId") Integer userId,
                                                     @Param("caseReference") String caseReference,
                                                     @Param("caseId") Long caseId,
                                                     @Param("resultId") Long resultId,
                                                     @Param("version") Integer version);

    @Data
    class CaseProjection {
        private Long caseId;
        private String caseNo;
        private String eyeSide;
        private String workflowStatus;
        private String qualityStatus;
        private String qualityReason;
        private Integer signedReportCount;
        private LocalDateTime updatedAt;
    }

    @Data
    class ProgressProjection {
        private Long caseId;
        private String caseNo;
        private Integer imageCount;
        private String qualityStatus;
        private String qualityReason;
        private String analysisStatus;
        private Integer signedReportCount;
        private LocalDateTime updatedAt;
    }
}
