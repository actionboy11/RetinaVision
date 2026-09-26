package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_skill_execution_log")
public class AgentSkillExecutionLogEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long sessionId;
    private Integer userId;
    private String traceId;
    private String skillCode;
    private Integer skillVersion;
    private Double confidence;
    private Boolean success;
    private Long latencyMs;
    private String errorType;
    private LocalDateTime createdAt;
}
