package com.example.retinavision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.pojo.DTO.TaskListQueryDTO;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.TaskListItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaskMapper extends BaseMapper<TaskEntity> {


    Long countTaskPage( @Param("query") TaskListQueryDTO safeQuery);

    List<TaskListItemVO> selectTaskPage(
            @Param("query") TaskListQueryDTO safeQuery,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize);

    TaskListItemVO getTaskById(@Param("taskId") Long taskId);

    Long countUnfinishedTask(@Param("imageFileId") Long imageFileId,
                             @Param("taskType") TaskType taskType);
}
