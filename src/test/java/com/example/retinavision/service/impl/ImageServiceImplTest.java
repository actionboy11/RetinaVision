package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.DTO.CreateTaskDTO;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceImplTest {
    @Mock ImageMapper imageMapper;
    @Mock CaseMapper caseMapper;
    @Mock UserRegisterMapper userMapper;
    @Mock TaskService taskService;
    @TempDir Path tempDir;

    @Test
    void uploadAutomaticallyCreatesQualityTask() throws Exception {
        when(caseMapper.selectById(10)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        doAnswer(invocation -> { ((ImageFileEntity) invocation.getArgument(0)).setId(20L); return 1; })
                .when(imageMapper).insert(any(ImageFileEntity.class));
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        MockMultipartFile file = new MockMultipartFile("file", "retina.png", "image/png", output.toByteArray());
        ImageServiceImpl service = new ImageServiceImpl(
                imageMapper, caseMapper, userMapper, taskService, tempDir.toString());

        service.uploadImage(10, file, 7);

        org.mockito.ArgumentCaptor<CreateTaskDTO> captor = org.mockito.ArgumentCaptor.forClass(CreateTaskDTO.class);
        verify(taskService).createTask(captor.capture(), org.mockito.ArgumentMatchers.eq(7));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getTaskType())
                .isEqualTo(TaskType.IMAGE_QUALITY_CHECK);
    }
}
