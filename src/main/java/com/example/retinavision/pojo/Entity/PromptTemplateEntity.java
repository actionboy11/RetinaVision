package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_template")
public class PromptTemplateEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String name;
    private String scenario;
    private String description;
    private String status;
    private Long activeVersionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
