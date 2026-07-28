package com.example.retinavision.analysis.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.retinavision.analysis.application.port.out.AnalysisTaskRepository;
import com.example.retinavision.analysis.domain.model.AnalysisTask;
import com.example.retinavision.analysis.domain.model.AnalysisTaskStatus;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.TaskEntity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

public final class LegacyAnalysisTaskRepository implements AnalysisTaskRepository {

    private final TaskMapper taskMapper;
    private final Clock clock;

    public LegacyAnalysisTaskRepository(TaskMapper taskMapper, Clock clock) {
        this.taskMapper = taskMapper;
        this.clock = clock;
    }

    @Override
    public Optional<AnalysisTask> findById(long taskId) {
        TaskEntity entity = taskMapper.selectById(taskId);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(AnalysisTask.rehydrate(
                entity.getId(),
                entity.getTaskNo(),
                entity.getImageFileId(),
                AnalysisTaskType.valueOf(entity.getTaskType().name()),
                AnalysisTaskStatus.valueOf(entity.getStatus().name()),
                entity.getRetryCount(),
                entity.getMaxRetryCount(),
                entity.getErrorMessage()));
    }

    @Override
    public boolean claim(AnalysisTask task, LocalDateTime startedAt) {
        return taskMapper.claimForExecution(task.id(), startedAt) == 1;
    }

    @Override
    public void save(AnalysisTask task) {
        LocalDateTime now = LocalDateTime.now(clock);
        UpdateWrapper<TaskEntity> update = new UpdateWrapper<TaskEntity>()
                .eq("id", task.id())
                .set("status", TaskStatus.valueOf(task.status().name()))
                .set("retry_count", task.retryCount())
                .set("error_message", task.errorMessage())
                .set("updated_at", now);
        if (task.status() == AnalysisTaskStatus.SUCCESS
                || task.status() == AnalysisTaskStatus.FAILED) {
            update.set("finished_at", now);
        }
        taskMapper.update(null, update);
    }
}
