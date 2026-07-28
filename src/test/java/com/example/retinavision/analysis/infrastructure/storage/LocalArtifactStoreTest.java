package com.example.retinavision.analysis.infrastructure.storage;

import com.example.retinavision.analysis.application.model.SourceImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalArtifactStoreTest {

    @TempDir
    Path tempDir;

    private Path imageRoot;
    private Path resultRoot;
    private LocalArtifactStore store;

    @BeforeEach
    void setUp() throws Exception {
        imageRoot = Files.createDirectories(tempDir.resolve("images"));
        resultRoot = Files.createDirectories(tempDir.resolve("results"));
        store = new LocalArtifactStore(imageRoot.toString(), resultRoot.toString());
    }

    @Test
    void rejectsTraversalAbsoluteOutsideAndAbsentSourceKeys() throws Exception {
        Path outside = Files.write(tempDir.resolve("outside.png"), new byte[]{1});

        assertInvalidSource(null);
        assertInvalidSource(" ");
        assertInvalidSource("../outside.png");
        assertInvalidSource(outside.toAbsolutePath().toString());
        assertInvalidSource("missing.png");
    }

    @Test
    void resolvesOnlyRegularFilesUnderConfiguredImageRoot() throws Exception {
        Path source = imageRoot.resolve("cases/10/source.png");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[]{1});

        Path resolved = store.resolveSource(source("cases/10/source.png"));

        assertThat(resolved).isEqualTo(source.toAbsolutePath().normalize());
    }

    @Test
    void rejectsNullOrEmptyMaskBytes() {
        assertThatThrownBy(() -> store.storeMask(100L, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.storeMask(100L, new byte[0]))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void atomicallyReplacesExactMaskTargetAndLeavesNoTemporaryFile() throws Exception {
        Path target = resultRoot.resolve("tasks/100/mask.png");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{9});

        String objectKey = store.storeMask(100L, new byte[]{1, 2, 3});

        assertThat(objectKey).isEqualTo("tasks/100/mask.png");
        assertThat(Files.readAllBytes(target)).containsExactly(1, 2, 3);
        try (var files = Files.list(target.getParent())) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactly("mask.png");
        }
    }

    private void assertInvalidSource(String objectKey) {
        assertThatThrownBy(() -> store.resolveSource(source(objectKey)))
                .isInstanceOf(IllegalStateException.class);
    }

    private SourceImage source(String objectKey) {
        return new SourceImage(20L, "source.png", "image/png", objectKey);
    }
}
