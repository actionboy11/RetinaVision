package com.example.retinavision.analysis.infrastructure.image;

import com.example.retinavision.analysis.application.model.SourceImage;
import com.example.retinavision.analysis.application.port.out.SourceImageReader;
import com.example.retinavision.enumeration.ImageStatus;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.pojo.Entity.ImageFileEntity;

public final class MyBatisSourceImageReader implements SourceImageReader {

    private final ImageMapper imageMapper;

    public MyBatisSourceImageReader(ImageMapper imageMapper) {
        this.imageMapper = imageMapper;
    }

    @Override
    public SourceImage getRequired(long imageFileId) {
        ImageFileEntity image = imageMapper.selectById(imageFileId);
        if (image == null
                || image.getDeletedAt() != null
                || image.getStatus() == ImageStatus.DELETED) {
            throw new IllegalStateException("任务关联图像不存在或已删除");
        }
        return new SourceImage(
                image.getId(),
                image.getOriginalFilename(),
                image.getFileType(),
                image.getStorageObjectKey());
    }
}
