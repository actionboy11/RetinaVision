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
    // 队列中待处理消息的数量
    private Long messageReadyCount;
    // 队列中未确认消息的数量
    private Long messageUnackedCount;
    private Long consumerCount;
    private Long deadLetterCount;
}
