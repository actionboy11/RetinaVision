package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_evaluation_run")
public class PromptEvaluationRunEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private Long baselineVersionId;
    private Long candidateVersionId;
    private String sampleVersion;
    private String provider;
    private String model;
    private String embeddingModel;
    private Double scoreThreshold;
    private String status;
    private String resultJson;
    private Boolean automatedPass;
    private String reviewDecision;
    private Integer reviewScore;
    private String reviewNote;
    private Integer reviewedBy;
    private LocalDateTime reviewedAt;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String failureReason;
}
