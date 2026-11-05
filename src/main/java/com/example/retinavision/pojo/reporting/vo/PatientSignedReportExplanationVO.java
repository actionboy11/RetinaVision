package com.example.retinavision.pojo.reporting.vo;

public record PatientSignedReportExplanationVO(Long resultId, Integer version,
                                               String findings, String conclusion,
                                               String recommendation) {
}
