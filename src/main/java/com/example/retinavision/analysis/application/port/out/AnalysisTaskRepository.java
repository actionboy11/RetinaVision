package com.example.retinavision.analysis.application.port.out;

import com.example.retinavision.analysis.domain.model.AnalysisTask;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AnalysisTaskRepository {

    Optional<AnalysisTask> findById(long taskId);

    boolean claim(AnalysisTask task, LocalDateTime startedAt);

    void save(AnalysisTask task);
}
