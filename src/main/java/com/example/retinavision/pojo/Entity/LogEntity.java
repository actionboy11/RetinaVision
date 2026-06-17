package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.TaskStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("task_log")
public class LogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private TaskStatus fromStatus;   //创建前没有旧状态
    private TaskStatus toStatus;     //创建后有新状态，创建后任务进入等待处理状态
    private String message;      //给前端时间线看的说明文字
    private  String operatorType;
    private Integer operatorId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss" , timezone = "GMT+8")
    private LocalDateTime createdAt;
}
