package com.example.retinavision.pojo.patient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.patient.PatientProfileSource;
import com.example.retinavision.enumeration.patient.PatientProfileStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("patient_profile")
public class PatientProfileEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String patientNo;
    private Integer accountUserId;
    private PatientProfileSource source;
    private String legacyPatientCode;
    private Integer createdBy;
    private PatientProfileStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
