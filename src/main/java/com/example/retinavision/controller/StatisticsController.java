package com.example.retinavision.controller;

import com.example.retinavision.pojo.VO.QueueStatisticsVO;
import com.example.retinavision.pojo.VO.TaskStatisticsVO;
import com.example.retinavision.pojo.VO.TaskTrendItemVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.StatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/statistics")
public class StatisticsController {
    // 统计服务接口，用于获取任务统计信息和趋势数据
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/tasks")
    public Result<TaskStatisticsVO> getTaskStatistics() {
        return Result.success(statisticsService.getTaskStatistics());
    }

    @GetMapping("/queue")
    public Result<QueueStatisticsVO> getQueueStatistics() {
        return Result.success(statisticsService.getQueueStatistics());
    }

    @GetMapping("/task-trend")
    // 获取任务趋势数据，参数 days 表示查询的天数，默认值为 7 天
    public Result<List<TaskTrendItemVO>> getTaskTrend(@RequestParam Integer days) {
        return Result.success(statisticsService.getTaskTrend(days));
    }
}
