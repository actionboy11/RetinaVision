package com.example.retinavision.analysis.infrastructure.persistence;

import com.example.retinavision.analysis.application.port.out.TaskAuditLog;
import com.example.retinavision.analysis.domain.model.TaskTransition;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.pojo.Entity.LogEntity;

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
