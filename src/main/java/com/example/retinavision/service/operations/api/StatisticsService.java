package com.example.retinavision.service.operations.api;

import com.example.retinavision.pojo.operations.vo.QueueStatisticsVO;
import com.example.retinavision.pojo.operations.vo.TaskStatisticsVO;
import com.example.retinavision.pojo.operations.vo.TaskTrendItemVO;

import java.util.List;

public interface StatisticsService {
    TaskStatisticsVO getTaskStatistics();

    QueueStatisticsVO getQueueStatistics();

    List<TaskTrendItemVO> getTaskTrend(Integer days);
}
