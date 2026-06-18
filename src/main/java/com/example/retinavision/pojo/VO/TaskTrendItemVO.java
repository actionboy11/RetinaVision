package com.example.retinavision.pojo.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskTrendItemVO {
    private String date;
    private Long submittedCount;
    private Long successCount;
    private Long failedCount;
}
