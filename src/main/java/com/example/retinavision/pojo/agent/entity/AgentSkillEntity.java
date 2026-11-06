package com.example.retinavision.pojo.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_skill")
public class AgentSkillEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String skillCode;
    private String name;
    private String description;
    private String status;
    private Long activeVersionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
