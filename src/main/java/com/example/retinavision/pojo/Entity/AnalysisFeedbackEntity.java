package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.FeedbackVerdict;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("analysis_feedback")
public class AnalysisFeedbackEntity {
    @TableId(value="id", type=IdType.AUTO) private Long id;
    private Long resultId;
    private FeedbackVerdict verdict;
    private String issueCodes;
    private String comment;
    private Integer submittedBy;
    private LocalDateTime createdAt;
}
