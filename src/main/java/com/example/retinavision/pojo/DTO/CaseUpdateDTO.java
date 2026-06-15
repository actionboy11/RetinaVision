package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.PatientGender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CaseUpdateDTO {
    private Integer patientAge;
    private PatientGender patientGender;
    private EyeSide eyeSide;
    private String diagnosisNote;
    private CaseStatus status;        // 对应前端的 'ACTIVE' | 'ARCHIVED'，要更新哪条记录由路径 /cases/{caseId} 决定。
}

