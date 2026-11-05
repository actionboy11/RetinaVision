package com.example.retinavision.service.agent;

import com.example.retinavision.agent.PatientCaseSearchCriteria;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
import com.example.retinavision.pojo.patient.vo.PatientAgentCaseSummaryVO;
import com.example.retinavision.pojo.patient.vo.PatientAgentProgressVO;
import com.example.retinavision.pojo.reporting.vo.PatientAgentReportDetailVO;
import com.example.retinavision.pojo.reporting.vo.PatientAgentSignedReportVO;
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
