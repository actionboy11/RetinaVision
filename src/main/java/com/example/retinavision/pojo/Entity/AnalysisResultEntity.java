package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.TaskType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("analysis_result")
public class AnalysisResultEntity {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private TaskType resultType;
    private String resultJson;
    private String maskBucket;
    private String maskObjectKey;
    private String maskPreviewUrl;
    private String reportBucket;
    private String reportObjectKey;
    private String reportDownloadUrl;
    private String modelName;
    private String modelVersion;
    private  Integer processingTimeMs;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

}
