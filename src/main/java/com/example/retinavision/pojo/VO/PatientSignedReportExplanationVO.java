package com.example.retinavision.pojo.VO;

public record PatientSignedReportExplanationVO(Long resultId, Integer version,
                                               String findings, String conclusion,
                                               String recommendation) {
}
