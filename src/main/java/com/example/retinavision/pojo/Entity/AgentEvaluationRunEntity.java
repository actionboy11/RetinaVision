package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_evaluation_run")
public class AgentEvaluationRunEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long datasetId;
    private Integer datasetVersion;
    private String targetRole;
    private String modelKey;
    private String provider;
    private String model;
    private String status;
    private Integer totalCount;
    private Integer completedCount;
    private Double routingAccuracy;
    private Double parameterAccuracy;
    private Double queryAccuracy;
    private Double structurePassRate;
    private Double safetyPassRate;
    private Double citationPassRate;
    private Long averageLatencyMs;
    private Long p95LatencyMs;
    private Boolean automatedPass;
    private Boolean cancelRequested;
    private String reviewDecision;
    private String reviewNote;
    private Integer reviewedBy;
    private LocalDateTime reviewedAt;
    private String errorSummary;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
