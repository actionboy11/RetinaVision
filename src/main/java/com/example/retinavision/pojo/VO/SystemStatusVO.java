package com.example.retinavision.pojo.VO;

import com.example.retinavision.ai.dto.AiHealthResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusVO {
    private String overallStatus;
    private OffsetDateTime checkedAt;
    private AiHealthResponse ai;
    private QueueStatus queue;
    private TaskStatisticsVO tasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueStatus {
        private String status;
        private String error;
        private String queueName;
        private Long messageReadyCount;
        private Long messageUnackedCount;
        private Long consumerCount;
        private Long deadLetterCount;
    }
}
