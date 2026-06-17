package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.common.Deletable;
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
@TableName("medical_case")
public class CaseEntity implements Deletable {
    @TableId(value = "id",type = IdType.AUTO)
    private Integer id;
    private String  caseNo;
    private String  patientCode;
    private Integer patientAge;
    private PatientGender patientGender;
    private EyeSide eyeSide;
    private String  diagnosisNote;
    private CaseStatus status;
    private Integer createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime updatedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime deletedAt;
    @Override
    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

}
