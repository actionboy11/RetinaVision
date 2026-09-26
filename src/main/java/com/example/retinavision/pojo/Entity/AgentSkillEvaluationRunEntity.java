package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_skill_evaluation_run")
public class AgentSkillEvaluationRunEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long skillVersionId;
    private String status;
    private Integer totalCount;
    private Integer routePassedCount;
    private Integer parameterPassedCount;
    private Double routingAccuracy;
    private Double parameterAccuracy;
    private Boolean safetyPassed;
    private String failureSamplesJson;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
