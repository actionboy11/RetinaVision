package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.PatientProfileSource;

import java.time.LocalDateTime;

public record PatientProfileVO(Long id, String patientNo, PatientProfileSource source,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
}
