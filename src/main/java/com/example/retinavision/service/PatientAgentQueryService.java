package com.example.retinavision.service;

import com.example.retinavision.agent.PatientCaseSearchCriteria;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PatientAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.PatientAgentProgressVO;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;
import com.example.retinavision.pojo.VO.PatientAgentSignedReportVO;
import com.example.retinavision.result.PageResult;

public interface PatientAgentQueryService {
    PageResult<PatientAgentCaseSummaryVO> listMyCases(PatientCaseSearchCriteria criteria,
                                                       int page, int pageSize, CurrentUserVO patient);

    PatientAgentProgressVO getMyCaseProgress(String caseReference, CurrentUserVO patient);

    PageResult<PatientAgentSignedReportVO> listMySignedReports(String caseReference,
                                                               int page, int pageSize, CurrentUserVO patient);

    PatientAgentReportDetailVO getMySignedReport(String caseReference, Long resultId,
                                                  Integer version, CurrentUserVO patient);
}
