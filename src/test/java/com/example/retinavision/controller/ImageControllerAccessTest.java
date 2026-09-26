package com.example.retinavision.controller;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.ImageFileItemVO;
import com.example.retinavision.service.ClinicalAccessService;
import com.example.retinavision.service.ImageService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageControllerAccessTest {
    private final ImageService images = mock(ImageService.class);
    private final ClinicalAccessService access = mock(ClinicalAccessService.class);
    private final ImageController controller = new ImageController(images, access);
    private final CurrentUserVO patient = new CurrentUserVO(7, "patient", "患者", UserRole.USER);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(patient, null);

    @Test
    void previewRequiresReadAccessButNotDraftMutationAccess() throws Exception {
        Path file = Files.createTempFile("retina-preview", ".png");
        try {
            ImageFileItemVO image = new ImageFileItemVO();
            image.setFileType("image/png");
            when(images.getImageById(10L)).thenReturn(image);
            when(images.getImagePreviewPath(10L)).thenReturn(file);

            assertThat(controller.previewImage(10L, authentication).getStatusCode().is2xxSuccessful()).isTrue();

            verify(access).assertCanAccessImage(patient, 10L);
            verify(access, never()).assertCanModifyImage(patient, 10L);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void deleteRequiresMutationAccess() {
        when(images.deleteImage(10L)).thenReturn(true);

        controller.deleteImage(10L, authentication);

        verify(access).assertCanModifyImage(patient, 10L);
        verify(access, never()).assertCanAccessImage(patient, 10L);
    }
}
