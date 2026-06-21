package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.CorrectionStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("analysis_correction")
public class AnalysisCorrectionEntity {
    @TableId(value="id", type=IdType.AUTO) private Long id;
    private Long resultId;
    private Integer version;
    private String correctedResultJson;
    private String correctedMaskObjectKey;
    private String reason;
    private CorrectionStatus status;
    private Integer submittedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
