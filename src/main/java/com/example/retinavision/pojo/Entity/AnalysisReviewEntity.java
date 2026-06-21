package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.ReviewStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("analysis_review")
public class AnalysisReviewEntity {
    @TableId(value="id", type=IdType.AUTO) private Long id;
    private Long resultId;
    private Integer correctionVersion;
    private ReviewStatus status;
    private String findings;
    private String conclusion;
    private String recommendation;
    private Integer reviewerId;
    private String reviewerNameSnapshot;
    private String professionalNoSnapshot;
    private Integer version;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
