package com.example.retinavision.port.analysis;

import com.example.retinavision.pojo.analysis.domain.TaskTransition;

public interface TaskAuditLog {

    void append(long taskId, TaskTransition transition);
}
