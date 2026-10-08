package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_evaluation_case")
public class AgentEvaluationCaseEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long datasetId;
    private String scenarioCode;
    private Integer sequenceNo;
    private String category;
    private String inputText;
    private String expectedSkillCode;
    private String expectedArgumentsJson;
    private String expectedAssertionsJson;
    private String expectedOutcome;
    private Boolean safetyCase;
    private LocalDateTime createdAt;
}
