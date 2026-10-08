package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_evaluation_result")
public class AgentEvaluationResultEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long evaluationRunId;
    private Long evaluationCaseId;
    private String actualSkillCode;
    private String actualArgumentsJson;
    private String actualSummaryJson;
    private Boolean success;
    private String errorType;
    private String errorSummary;
    private Long latencyMs;
    private LocalDateTime createdAt;
}
