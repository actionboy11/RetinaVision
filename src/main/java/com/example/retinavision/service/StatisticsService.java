package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.QueueStatisticsVO;
import com.example.retinavision.pojo.VO.TaskStatisticsVO;
import com.example.retinavision.pojo.VO.TaskTrendItemVO;

import java.util.List;

public interface StatisticsService {
    TaskStatisticsVO getTaskStatistics();

    QueueStatisticsVO getQueueStatistics();

    List<TaskTrendItemVO> getTaskTrend(Integer days);
}
