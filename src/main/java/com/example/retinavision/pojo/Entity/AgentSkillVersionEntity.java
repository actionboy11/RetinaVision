package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_skill_version")
public class AgentSkillVersionEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long skillId;
    private Integer version;
    private String routingExamplesJson;
    private String routingNegativeExamplesJson;
    private String workflowPrompt;
    private String answerStyle;
    private String errorPromptsJson;
    private Integer createdBy;
    private LocalDateTime createdAt;
}
