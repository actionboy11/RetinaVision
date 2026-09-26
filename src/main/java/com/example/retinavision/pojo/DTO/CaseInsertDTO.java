package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.PatientGender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CaseInsertDTO {
    private Long patientId;
    private Integer patientAge;
    private PatientGender patientGender;
    private EyeSide eyeSide;
    private String diagnosisNote;
    private Integer assignedDoctorId;
}
