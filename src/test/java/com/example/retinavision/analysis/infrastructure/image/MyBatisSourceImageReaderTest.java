package com.example.retinavision.analysis.infrastructure.image;

import com.example.retinavision.analysis.application.model.SourceImage;
import com.example.retinavision.enumeration.ImageStatus;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisSourceImageReaderTest {

    private final ImageMapper imageMapper = mock(ImageMapper.class);
    private final MyBatisSourceImageReader reader = new MyBatisSourceImageReader(imageMapper);

    @Test
    void rejectsMissingSoftDeletedAndDeletedImages() {
        when(imageMapper.selectById(1L)).thenReturn(null);
        when(imageMapper.selectById(2L)).thenReturn(ImageFileEntity.builder()
                .id(2L).status(ImageStatus.UPLOADED).deletedAt(LocalDateTime.now()).build());
        when(imageMapper.selectById(3L)).thenReturn(ImageFileEntity.builder()
                .id(3L).status(ImageStatus.DELETED).build());

        assertUnavailable(1L);
        assertUnavailable(2L);
        assertUnavailable(3L);
    }

    @Test
    void mapsAvailableLegacyEntityToProviderNeutralSourceImage() {
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder()
                .id(20L)
                .status(ImageStatus.BOUND_TASK)
                .originalFilename("fundus.png")
                .fileType("image/png")
                .storageObjectKey("cases/10/fundus.png")
                .build());

        SourceImage image = reader.getRequired(20L);

        assertThat(image).isEqualTo(new SourceImage(
                20L, "fundus.png", "image/png", "cases/10/fundus.png"));
        assertThat(image.getClass().getPackageName())
                .isEqualTo("com.example.retinavision.analysis.application.model");
    }

    private void assertUnavailable(long imageId) {
        assertThatThrownBy(() -> reader.getRequired(imageId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("任务关联图像不存在或已删除");
    }
}
