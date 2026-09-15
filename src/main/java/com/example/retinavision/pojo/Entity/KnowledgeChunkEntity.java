package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunkEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String chunkText;
    private Integer charLength;
    private String qdrantPointId;
    private LocalDateTime createdAt;
}
