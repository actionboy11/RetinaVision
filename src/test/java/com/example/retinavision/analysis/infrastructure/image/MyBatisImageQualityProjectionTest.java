package com.example.retinavision.analysis.infrastructure.image;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisImageQualityProjectionTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T06:07:08Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 28, 6, 7, 8);

    private ImageMapper imageMapper;
    private MyBatisImageQualityProjection projection;

    @BeforeEach
    void setUp() {
        imageMapper = mock(ImageMapper.class);
        projection = new MyBatisImageQualityProjection(imageMapper, CLOCK);
    }

    @Test
    void marksOnlyCurrentQualityTaskErrorWithConditionalUpdate() {
        when(imageMapper.update(
                isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<ImageFileEntity>>any()))
                .thenReturn(1);

        projection.markErrorIfCurrent(20L, 100L);

        UpdateWrapper<ImageFileEntity> update = captureUpdate();
        assertThat(update.getExpression().getSqlSegment())
                .contains("id")
                .contains("quality_task_id");
        assertThat(update.getSqlSet())
                .contains("quality_status")
                .contains("updated_at")
                .doesNotContain(
                        "quality_score",
                        "quality_result_id",
                        "quality_checked_at",
                        "quality_task_id");
        assertThat(update.getSqlSet().split(",")).hasSize(2);
        assertThat(update.getParamNameValuePairs().values())
                .contains(20L, 100L, ImageQualityStatus.ERROR, NOW);
        verify(imageMapper, never()).updateById(any(ImageFileEntity.class));
        verify(imageMapper, never()).selectById(any());
    }

    @Test
    void zeroUpdatedRowsTreatsSupersededQualityTaskAsSafeNoOp() {
        when(imageMapper.update(
                isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<ImageFileEntity>>any()))
                .thenReturn(0);

        assertThatCode(() -> projection.markErrorIfCurrent(20L, 100L))
                .doesNotThrowAnyException();

        UpdateWrapper<ImageFileEntity> update = captureUpdate();
        assertThat(update.getExpression().getSqlSegment())
                .contains("id")
                .contains("quality_task_id");
        assertThat(update.getParamNameValuePairs().values())
                .contains(20L, 100L);
        verify(imageMapper, never()).updateById(any(ImageFileEntity.class));
        verify(imageMapper, never()).selectById(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private UpdateWrapper<ImageFileEntity> captureUpdate() {
        ArgumentCaptor<UpdateWrapper<ImageFileEntity>> captor =
                ArgumentCaptor.forClass((Class) UpdateWrapper.class);
        verify(imageMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }
}
