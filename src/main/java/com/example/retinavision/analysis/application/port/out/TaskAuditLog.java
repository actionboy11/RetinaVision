package com.example.retinavision.analysis.application.port.out;

import com.example.retinavision.analysis.domain.model.TaskTransition;

public interface TaskAuditLog {

    void append(long taskId, TaskTransition transition);
}
