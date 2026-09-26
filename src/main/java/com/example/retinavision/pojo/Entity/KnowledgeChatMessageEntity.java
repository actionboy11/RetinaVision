package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_chat_message")
public class KnowledgeChatMessageEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer userId;
    private String role;
    private String content;
    private String citationsJson;
    private String llmProvider;
    private String llmModel;
    private LocalDateTime createdAt;
}
