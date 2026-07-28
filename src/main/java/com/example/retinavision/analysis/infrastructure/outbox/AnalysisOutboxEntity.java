package com.example.retinavision.analysis.infrastructure.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("analysis_outbox")
public class AnalysisOutboxEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String eventKey;
    private Long aggregateId;
    private String eventType;
    private Integer eventVersion;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime publishedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
