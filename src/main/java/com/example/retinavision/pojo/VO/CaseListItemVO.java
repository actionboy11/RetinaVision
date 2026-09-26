package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.PatientGender;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CaseListItemVO {
    private Integer id;
    private String caseNo;
    private Long patientId;
    private String patientNo;
    /** Historical read-only identifier retained for migrated records. */
    private String patientCode;
    private Integer patientAge;
    private PatientGender patientGender;
    private EyeSide eyeSide;
    private CaseStatus status;
    private CaseWorkflowStatus workflowStatus;
    private String diagnosisNote;
    private Integer createdBy;
    private String createdByName;
    private Integer assignedDoctorId;
    private String assignedDoctorName;
    private String assignedDoctorProfessionalNo;
    // 返回给前端列表展示的时间格式，和 API_CONTRACT.md 中的示例保持一致。
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
