package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_chat_message")
public class AgentChatMessageEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer userId;
    private String role;
    private String content;
    private String llmProvider;
    private String llmModel;
    private String structuredContentJson;
    private LocalDateTime createdAt;
}
