package com.example.retinavision.mapper.analysis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retinavision.pojo.analysis.entity.LogEntity;
import com.example.retinavision.pojo.analysis.vo.TaskLogVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface LogMapper extends BaseMapper<LogEntity> {
    //根据任务id查询任务日志
    @Select("select * from task_log where task_id=#{taskId} order by created_at asc, id asc")
    List<TaskLogVO> selectByTaskId(Integer taskId);
}
