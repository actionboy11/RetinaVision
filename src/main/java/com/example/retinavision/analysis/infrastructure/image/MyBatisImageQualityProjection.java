package com.example.retinavision.analysis.infrastructure.image;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.retinavision.analysis.application.port.out.ImageQualityProjectionPort;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.pojo.Entity.ImageFileEntity;

import java.time.Clock;
import java.time.LocalDateTime;

public final class MyBatisImageQualityProjection
        implements ImageQualityProjectionPort {

    private final ImageMapper imageMapper;
    private final Clock clock;

    public MyBatisImageQualityProjection(ImageMapper imageMapper, Clock clock) {
        this.imageMapper = imageMapper;
        this.clock = clock;
    }

    @Override
    public void markErrorIfCurrent(long imageFileId, long taskId) {
        LocalDateTime now = LocalDateTime.now(clock);
        imageMapper.update(null, new UpdateWrapper<ImageFileEntity>()
                .eq("id", imageFileId)
                .eq("quality_task_id", taskId)
                .set("quality_status", ImageQualityStatus.ERROR)
                .set("updated_at", now));
    }
}
