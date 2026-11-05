package com.example.retinavision.pojo.analysis.dto;

public record SourceImage(
        long id,
        String originalFilename,
        String contentType,
        String storageObjectKey) {
}
