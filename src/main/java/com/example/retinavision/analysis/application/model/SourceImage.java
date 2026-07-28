package com.example.retinavision.analysis.application.model;

public record SourceImage(
        long id,
        String originalFilename,
        String contentType,
        String storageObjectKey) {
}
