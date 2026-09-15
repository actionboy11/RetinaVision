package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.LogEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.service.ClinicalTaskLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ClinicalTaskLogServiceImpl implements ClinicalTaskLogService {

    private final AnalysisResultMapper results;
    private final TaskMapper tasks;
    private final LogMapper logs;

    public ClinicalTaskLogServiceImpl(AnalysisResultMapper results, TaskMapper tasks, LogMapper logs) {
        this.results = results;
        this.tasks = tasks;
        this.logs = logs;
    }

    @Override
    public void appendResultEvent(Long resultId, String message, String operatorType, Integer operatorId) {
        AnalysisResultEntity result = results.selectById(resultId);
        if (result == null || result.getTaskId() == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析结果不存在或未关联任务");
        }

        TaskEntity task = tasks.selectById(result.getTaskId());
        if (task == null || task.getStatus() == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析任务不存在或状态异常");
        }

        logs.insert(LogEntity.builder()
                .taskId(task.getId())
                .fromStatus(task.getStatus())
                .toStatus(task.getStatus())
                .message(message)
                .operatorType(operatorType)
                .operatorId(operatorId)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
