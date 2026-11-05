package com.example.retinavision.service.patient;

import com.example.retinavision.agent.PatientReportExplanationResult;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;

public interface PatientReportExplanationService {
    PatientReportExplanationResult explain(PatientAgentReportDetailVO report);
}
