package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_template_version")
public class PromptTemplateVersionEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long templateId;
    @TableField(exist = false)
    private String templateCode;
    private Integer version;
    private String systemPrompt;
    private String outputContract;
    private String safetyPolicy;
    @TableField("is_active")
    private Boolean active;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;
}
