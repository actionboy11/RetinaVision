package com.example.retinavision.service.imaging.impl;
import com.example.retinavision.mapper.analysis.TaskMapper;

import com.example.retinavision.enumeration.cases.CaseStatus;
import com.example.retinavision.enumeration.analysis.TaskType;
import com.example.retinavision.mapper.cases.CaseMapper;
import com.example.retinavision.mapper.imaging.ImageMapper;
import com.example.retinavision.mapper.user.UserRegisterMapper;
import com.example.retinavision.pojo.analysis.dto.CreateTaskDTO;
import com.example.retinavision.pojo.cases.entity.CaseEntity;
import com.example.retinavision.pojo.imaging.entity.ImageFileEntity;
import com.example.retinavision.service.analysis.api.TaskService;
import com.example.retinavision.pojo.analysis.vo.CreateTaskVO;
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
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class ImageServiceImplTest {
    @Mock ImageMapper imageMapper;
    @Mock CaseMapper caseMapper;
    @Mock UserRegisterMapper userMapper;
    @Mock TaskService taskService;
    @Mock com.example.retinavision.mapper.analysis.TaskMapper taskMapper;
    @TempDir Path tempDir;

    @Test
    void uploadAutomaticallyCreatesQualityTask() throws Exception {
        when(caseMapper.selectById(10)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        doAnswer(invocation -> { ((ImageFileEntity) invocation.getArgument(0)).setId(20L); return 1; })
                .when(imageMapper).insert(any(ImageFileEntity.class));
        when(taskService.createTask(any(CreateTaskDTO.class), org.mockito.ArgumentMatchers.eq(7)))
                .thenReturn(CreateTaskVO.builder().id(33L).build());
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        MockMultipartFile file = new MockMultipartFile("file", "retina.png", "image/png", output.toByteArray());
        ImageServiceImpl service = new ImageServiceImpl(
                imageMapper, caseMapper, userMapper, taskService, taskMapper, tempDir.toString());

        service.uploadImage(10, file, 7);

        org.mockito.ArgumentCaptor<CreateTaskDTO> captor = org.mockito.ArgumentCaptor.forClass(CreateTaskDTO.class);
        verify(taskService).createTask(captor.capture(), org.mockito.ArgumentMatchers.eq(7));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getTaskType())
                .isEqualTo(TaskType.IMAGE_QUALITY_CHECK);
        org.mockito.ArgumentCaptor<ImageFileEntity> imageCaptor = org.mockito.ArgumentCaptor.forClass(ImageFileEntity.class);
        verify(imageMapper, atLeastOnce()).updateById(imageCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(imageCaptor.getAllValues().get(imageCaptor.getAllValues().size() - 1).getQualityTaskId())
                .isEqualTo(33L);
    }
}
