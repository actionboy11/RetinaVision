package com.example.retinavision.service.patient;

import com.example.retinavision.pojo.agent.PatientReportExplanationResult;

import com.example.retinavision.pojo.reporting.vo.PatientAgentReportDetailVO;

public interface PatientReportExplanationService {
    PatientReportExplanationResult explain(PatientAgentReportDetailVO report);
}
