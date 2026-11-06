package com.example.retinavision.pojo.patient.vo;

import com.example.retinavision.enumeration.patient.PatientProfileSource;

import java.time.LocalDateTime;

public record PatientProfileVO(Long id, String patientNo, PatientProfileSource source,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
}
