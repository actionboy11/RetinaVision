package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.ImageQualityStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ImageQualitySummaryVO {
    private ImageQualityStatus status;
    private Double score;
    private Long resultId;
    private Long taskId;
    private LocalDateTime checkedAt;
}
