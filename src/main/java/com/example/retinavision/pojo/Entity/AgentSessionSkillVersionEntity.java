package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_session_skill_version")
public class AgentSessionSkillVersionEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String skillCode;
    private Long skillVersionId;
    private LocalDateTime createdAt;
}
