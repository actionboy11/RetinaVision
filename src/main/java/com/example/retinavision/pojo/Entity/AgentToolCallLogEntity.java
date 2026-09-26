package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_call_log")
public class AgentToolCallLogEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer userId;
    private String traceId;
    private String toolName;
    private String argumentsSummary;
    private String resultSummary;
    private Boolean success;
    private Long latencyMs;
    private String errorSummary;
    private LocalDateTime createdAt;
}
