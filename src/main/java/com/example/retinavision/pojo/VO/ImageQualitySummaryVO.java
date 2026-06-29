package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.ImageQualityStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
// 图像质量摘要视图对象类
public class ImageQualitySummaryVO {
    private ImageQualityStatus status;
    private Double score;
    private Long resultId;
    private Long taskId;
    private LocalDateTime checkedAt;
}
