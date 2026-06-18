package com.example.retinavision.pojo.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QueueStatisticsVO {
    private String queueName;
    private Long messageReadyCount;
    private Long messageUnackedCount;
    private Long consumerCount;
    private Long deadLetterCount;
}
