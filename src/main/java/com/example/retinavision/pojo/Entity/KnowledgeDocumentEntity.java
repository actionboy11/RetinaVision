package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocumentEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String title;
    private String source;
    private String contentType;
    private String category;
    private String originalContent;
    private String status;
    private Integer version;
    private Integer uploadedBy;
    private Integer chunkCount;
    private String failureReason;
    private LocalDateTime lastIndexedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
