package com.example.retinavision.mapper.operations;

import com.example.retinavision.pojo.operations.vo.TaskStatisticsVO;
import com.example.retinavision.pojo.operations.vo.TaskTrendItemVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface StatisticsMapper {
    // 查询任务统计信息
    TaskStatisticsVO selectTaskStatistics();

    List<TaskTrendItemVO> selectTaskTrend(@Param("startDate") LocalDate startDate);
}
