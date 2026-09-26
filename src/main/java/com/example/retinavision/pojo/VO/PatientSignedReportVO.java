package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.ReportStatus;

import java.time.LocalDateTime;

public record PatientSignedReportVO(Long resultId, Integer version, ReportStatus status,
                                    LocalDateTime signedAt, String signerName,
                                    String sha256) {
}
