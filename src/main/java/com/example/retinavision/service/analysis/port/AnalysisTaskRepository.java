package com.example.retinavision.service.analysis.port;

import com.example.retinavision.pojo.analysis.domain.AnalysisTask;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AnalysisTaskRepository {

    Optional<AnalysisTask> findById(long taskId);

    boolean claim(AnalysisTask task, LocalDateTime startedAt);

    void save(AnalysisTask task);
}
