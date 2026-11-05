package com.example.retinavision.pojo.reporting.vo;

import com.example.retinavision.enumeration.reporting.ReportStatus;

import java.time.LocalDateTime;

public record PatientSignedReportVO(Long resultId, Integer version, ReportStatus status,
                                    LocalDateTime signedAt, String signerName,
                                    String sha256) {
}
