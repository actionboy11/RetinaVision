package com.example.retinavision.service.analysis.adapter;

import com.example.retinavision.pojo.analysis.dto.SourceImage;
import com.example.retinavision.service.analysis.port.SourceImageReader;
import com.example.retinavision.enumeration.imaging.ImageStatus;
import com.example.retinavision.mapper.imaging.ImageMapper;
import com.example.retinavision.pojo.imaging.entity.ImageFileEntity;

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
