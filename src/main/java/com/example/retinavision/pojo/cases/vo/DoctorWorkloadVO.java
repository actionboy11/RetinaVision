package com.example.retinavision.pojo.cases.vo;

public record DoctorWorkloadVO(long patientCount,
                               long caseCount,
                               long unsegmentedCaseCount,
                               long incompleteSegmentationCaseCount,
                               long pendingReviewCount,
                               long pendingReportCount) {
}
