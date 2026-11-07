package com.example.retinavision.infrastructure.analysis.persistence;

import com.example.retinavision.port.analysis.TaskAuditLog;
import com.example.retinavision.pojo.analysis.domain.TaskTransition;
import com.example.retinavision.enumeration.analysis.TaskStatus;
import com.example.retinavision.mapper.analysis.LogMapper;
import com.example.retinavision.pojo.analysis.entity.LogEntity;

public final class MyBatisTaskAuditLog implements TaskAuditLog {

    private final LogMapper logMapper;

    public MyBatisTaskAuditLog(LogMapper logMapper) {
        this.logMapper = logMapper;
    }

    @Override
    public void append(long taskId, TaskTransition transition) {
        logMapper.insert(LogEntity.builder()
                .taskId(taskId)
                .fromStatus(TaskStatus.valueOf(transition.from().name()))
                .toStatus(TaskStatus.valueOf(transition.to().name()))
                .message(transition.message())
                .operatorType("WORKER")
                .operatorId(null)
                .createdAt(transition.at())
                .build());
    }
}
