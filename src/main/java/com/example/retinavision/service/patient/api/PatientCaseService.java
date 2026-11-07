package com.example.retinavision.service.patient.api;

import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
import com.example.retinavision.pojo.patient.vo.PatientCaseProgressVO;
import com.example.retinavision.pojo.reporting.vo.PatientSignedReportVO;
import com.example.retinavision.pojo.reporting.vo.PatientSignedReportExplanationVO;

import java.util.List;

public interface PatientCaseService {
    PatientCaseProgressVO progress(Long caseId, CurrentUserVO patient);
    List<PatientSignedReportVO> signedReports(Long caseId, CurrentUserVO patient);
    void assertSignedReportAccessible(Long caseId, Long resultId, Integer version, CurrentUserVO patient);
    PatientSignedReportExplanationVO signedReportExplanation(Long caseId, Long resultId,
                                                             Integer version, CurrentUserVO patient);
}
