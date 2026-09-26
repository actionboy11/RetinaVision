package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PatientCaseProgressVO;
import com.example.retinavision.pojo.VO.PatientSignedReportVO;
import com.example.retinavision.pojo.VO.PatientSignedReportExplanationVO;

import java.util.List;

public interface PatientCaseService {
    PatientCaseProgressVO progress(Long caseId, CurrentUserVO patient);
    List<PatientSignedReportVO> signedReports(Long caseId, CurrentUserVO patient);
    void assertSignedReportAccessible(Long caseId, Long resultId, Integer version, CurrentUserVO patient);
    PatientSignedReportExplanationVO signedReportExplanation(Long caseId, Long resultId,
                                                             Integer version, CurrentUserVO patient);
}
