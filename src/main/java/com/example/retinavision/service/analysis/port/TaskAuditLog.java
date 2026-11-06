package com.example.retinavision.service.analysis.port;

import com.example.retinavision.pojo.analysis.domain.TaskTransition;

public interface TaskAuditLog {

    void append(long taskId, TaskTransition transition);
}
