package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.ReviewStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoctorClinicalQueueItemVO {
    private Long taskId;
    private String taskNo;
    private Long resultId;
    private Integer caseId;
    private String caseNo;
    private String patientNo;
    private EyeSide eyeSide;
    private String resultType;
    private String qualityStatus;
    private Double qualityScore;
    private ReviewStatus reviewStatus;
    private ReportStatus reportStatus;
    private LocalDateTime finishedAt;
    private LocalDateTime resultCreatedAt;
}
