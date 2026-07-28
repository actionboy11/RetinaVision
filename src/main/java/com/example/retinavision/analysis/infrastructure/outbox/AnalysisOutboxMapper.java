package com.example.retinavision.analysis.infrastructure.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalysisOutboxMapper extends BaseMapper<AnalysisOutboxEntity> {

    List<Long> selectDuePendingIds(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    int claimPending(
            @Param("id") long id,
            @Param("now") LocalDateTime now);

    AnalysisOutboxEntity selectClaimed(@Param("id") long id);

    int markPublished(
            @Param("id") long id,
            @Param("publishedAt") LocalDateTime publishedAt);

    int reschedule(
            @Param("id") long id,
            @Param("nextAttemptCount") int nextAttemptCount,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("safeError") String safeError,
            @Param("updatedAt") LocalDateTime updatedAt);

    int recoverStaleProcessing(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now);
}
