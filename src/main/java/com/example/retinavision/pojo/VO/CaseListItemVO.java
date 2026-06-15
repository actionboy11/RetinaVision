package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.CaseStatus;
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
    private String patientCode;
    private Integer patientAge;
    private PatientGender patientGender;
    private EyeSide eyeSide;
    private CaseStatus status;
    private String diagnosisNote;
    private Integer createdBy;
    private String createdByName;
    // 返回给前端列表展示的时间格式，和 API_CONTRACT.md 中的示例保持一致。
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
