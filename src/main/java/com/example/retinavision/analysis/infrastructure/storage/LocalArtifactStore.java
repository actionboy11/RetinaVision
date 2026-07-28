package com.example.retinavision.analysis.infrastructure.storage;

import com.example.retinavision.analysis.application.model.SourceImage;
import com.example.retinavision.analysis.application.port.out.ArtifactStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class LocalArtifactStore implements ArtifactStore {

    private final Path imageRoot;
    private final Path resultRoot;

    public LocalArtifactStore(String imageRoot, String resultRoot) {
        this.imageRoot = Path.of(imageRoot).toAbsolutePath().normalize();
        this.resultRoot = Path.of(resultRoot).toAbsolutePath().normalize();
    }

    public Path resolveSource(SourceImage image) {
        String objectKey = image.storageObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException("任务关联图像存储路径为空");
        }
        Path source = imageRoot.resolve(objectKey).normalize();
        if (!source.startsWith(imageRoot)) {
            throw new IllegalStateException("任务关联图像存储路径无效");
        }
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("任务关联图像文件不存在");
        }
        return source;
    }

    @Override
    public String storeMask(long taskId, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("AI 服务返回了空的分割结果图");
        }
        String objectKey = "tasks/" + taskId + "/mask.png";
        Path target = resultRoot.resolve(objectKey).normalize();
        if (!target.startsWith(resultRoot)) {
            throw new IllegalArgumentException("分割结果图存储路径无效");
        }

        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "mask-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return objectKey;
        } catch (IOException exception) {
            throw new IllegalStateException("无法存储分割结果图", exception);
        }
    }
}
