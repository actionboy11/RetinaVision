package com.example.retinavision.pojo.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskStatisticsVO {
    private Long todaySubmittedCount;
    private Long todaySuccessCount;
    private Long todayFailedCount;
    private Long waitingCount;
    private Long runningCount;
    private Long totalTaskCount;
    private Double successRate;
    private Long averageProcessingTimeMs;
}
