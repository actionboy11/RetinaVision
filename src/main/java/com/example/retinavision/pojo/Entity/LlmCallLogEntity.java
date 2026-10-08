package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_call_log")
public class LlmCallLogEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String scenario;
    private String templateCode;
    private Integer templateVersion;
    private String provider;
    private String model;
    private Boolean success;
    private Long latencyMs;
    private String errorSummary;
    private String callSource;
    private Long evaluationRunId;
    private LocalDateTime createdAt;
}
