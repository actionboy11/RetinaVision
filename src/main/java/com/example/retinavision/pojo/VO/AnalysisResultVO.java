package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.TaskType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnalysisResultVO {
    private Long id;
    private Long taskId;
    private TaskType resultType;
    private Map<String, Object> resultJson;
    private String maskPreviewUrl;
    private String reportDownloadUrl;
    private String modelName;
    private String modelVersion;
    private ImageQualitySummaryVO qualitySummary;
    private  Integer processingTimeMs;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

}
