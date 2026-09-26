package com.example.retinavision.pojo.VO;

public record DoctorWorkloadVO(long patientCount,
                               long caseCount,
                               long unsegmentedCaseCount,
                               long incompleteSegmentationCaseCount,
                               long pendingReviewCount,
                               long pendingReportCount) {
}
