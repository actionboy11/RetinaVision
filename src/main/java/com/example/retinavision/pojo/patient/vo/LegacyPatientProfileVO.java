package com.example.retinavision.pojo.patient.vo;

import java.time.LocalDateTime;

public record LegacyPatientProfileVO(Long id, String patientNo, String legacyPatientCode,
                                     Integer accountUserId, String accountUsername,
                                     Long caseCount, LocalDateTime createdAt) {
}
