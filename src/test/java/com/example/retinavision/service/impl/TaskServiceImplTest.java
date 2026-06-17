package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.ImageStatus;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.mq.AnalysisTaskMessagePublisher;
import com.example.retinavision.pojo.DTO.CreateTaskDTO;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.LogEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.CreateTaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ImageMapper imageMapper;
    @Mock
    private CaseMapper caseMapper;
    @Mock
    private UserRegisterMapper userRegisterMapper;
    @Mock
    private LogMapper logMapper;
    @Mock
    private AnalysisTaskMessagePublisher analysisTaskMessagePublisher;

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(
                taskMapper,
                imageMapper,
                caseMapper,
                userRegisterMapper,
                logMapper,
                analysisTaskMessagePublisher
        );
    }

    @Test
    void createTaskCreatesWaitingTaskAfterMqDelivery() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.VESSEL_SEGMENTATION, 5);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L).status(ImageStatus.UPLOADED).build());
        when(taskMapper.countUnfinishedTask(20L, TaskType.VESSEL_SEGMENTATION)).thenReturn(0L);
        doAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(taskMapper).insert(any(TaskEntity.class));

        CreateTaskVO result = taskService.createTask(dto, 7);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.WAITING);
        verify(analysisTaskMessagePublisher).publish(any());

        ArgumentCaptor<TaskEntity> insertedTask = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskMapper).insert(insertedTask.capture());
        assertThat(insertedTask.getValue().getStatus()).isEqualTo(TaskStatus.WAITING);

        ArgumentCaptor<LogEntity> logCaptor = ArgumentCaptor.forClass(LogEntity.class);
        verify(logMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getFromStatus()).isNull();
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo(TaskStatus.WAITING);
    }

    @Test
    void createTaskRejectsDuplicateUnfinishedTask() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.IMAGE_QUALITY_CHECK, 3);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L).status(ImageStatus.UPLOADED).build());
        when(taskMapper.countUnfinishedTask(20L, TaskType.IMAGE_QUALITY_CHECK)).thenReturn(1L);

        assertThatThrownBy(() -> taskService.createTask(dto, 7))
                .isInstanceOf(BaseException.class)
                .extracting("code")
                .isEqualTo(ErrorMessageSignal.CONFLICT);

        verify(taskMapper, never()).insert(any(TaskEntity.class));
        verify(analysisTaskMessagePublisher, never()).publish(any());
    }

    @Test
    void createTaskThrowsMqDeliveryErrorWhenPublisherFails() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.VESSEL_SEGMENTATION, 5);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L).status(ImageStatus.UPLOADED).build());
        when(taskMapper.countUnfinishedTask(20L, TaskType.VESSEL_SEGMENTATION)).thenReturn(0L);
        doAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(taskMapper).insert(any(TaskEntity.class));
        doThrow(new BaseException(ErrorMessageSignal.MQ_DELIVERY_ERROR, "MQ delivery failed"))
                .when(analysisTaskMessagePublisher).publish(any());

        assertThatThrownBy(() -> taskService.createTask(dto, 7))
                .isInstanceOf(BaseException.class)
                .extracting("code")
                .isEqualTo(ErrorMessageSignal.MQ_DELIVERY_ERROR);

        verify(taskMapper, never()).updateById(any(TaskEntity.class));
    }
}
