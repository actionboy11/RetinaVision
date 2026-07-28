package com.example.retinavision.service.impl;

import com.example.retinavision.analysis.application.model.AnalysisTaskRequestedEvent;
import com.example.retinavision.analysis.application.port.out.AnalysisTaskEventOutbox;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.ImageStatus;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.example.retinavision.enumeration.UserRole;
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
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.pojo.VO.CreateTaskVO;
import com.example.retinavision.service.AiQuotaReservation;
import com.example.retinavision.service.AiTaskQuotaService;
import com.example.retinavision.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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
    @Mock
    private AnalysisTaskEventOutbox analysisTaskEventOutbox;
    @Mock
    private AiTaskQuotaService aiTaskQuotaService;

    private final AiQuotaReservation reservation = new AiQuotaReservation("minute-key", "day-key");

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(
                taskMapper,
                imageMapper,
                caseMapper,
                userRegisterMapper,
                logMapper,
                analysisTaskMessagePublisher,
                analysisTaskEventOutbox,
                aiTaskQuotaService
        );
        lenient().when(aiTaskQuotaService.reserve(7)).thenReturn(reservation);
    }

    @Test
    void createTaskCommitsWaitingTaskAndOutboxEvent() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.VESSEL_SEGMENTATION, 5);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L)
                .status(ImageStatus.UPLOADED).qualityStatus(ImageQualityStatus.PASS).build());
        when(taskMapper.countUnfinishedTask(20L, TaskType.VESSEL_SEGMENTATION)).thenReturn(0L);
        doAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(taskMapper).insert(any(TaskEntity.class));

        CreateTaskVO result = taskService.createTask(dto, 7);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.WAITING);
        verifyNoInteractions(analysisTaskMessagePublisher);

        ArgumentCaptor<TaskEntity> insertedTask = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskMapper).insert(insertedTask.capture());
        assertThat(insertedTask.getValue().getStatus()).isEqualTo(TaskStatus.WAITING);

        ArgumentCaptor<AnalysisTaskRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(AnalysisTaskRequestedEvent.class);
        verify(analysisTaskEventOutbox).append(
                eventCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("ANALYSIS_TASK_REQUESTED"),
                org.mockito.ArgumentMatchers.eq(1));
        assertThat(eventCaptor.getValue()).isEqualTo(new AnalysisTaskRequestedEvent(
                100L,
                insertedTask.getValue().getTaskNo(),
                10L,
                20L,
                AnalysisTaskType.VESSEL_SEGMENTATION,
                5,
                7,
                insertedTask.getValue().getSubmittedAt()));

        ArgumentCaptor<LogEntity> logCaptor = ArgumentCaptor.forClass(LogEntity.class);
        verify(logMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getFromStatus()).isNull();
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo(TaskStatus.WAITING);
        assertThat(logCaptor.getValue().getMessage()).isEqualTo("任务已创建并等待消息发布");
    }

    @Test
    void createTaskRejectsDuplicateUnfinishedTask() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.IMAGE_QUALITY_CHECK, 3);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L)
                .status(ImageStatus.UPLOADED).qualityStatus(ImageQualityStatus.PASS).build());
        when(taskMapper.countUnfinishedTask(20L, TaskType.IMAGE_QUALITY_CHECK)).thenReturn(1L);

        assertThatThrownBy(() -> taskService.createTask(dto, 7))
                .isInstanceOf(BaseException.class)
                .extracting("code")
                .isEqualTo(ErrorMessageSignal.CONFLICT);

        verify(taskMapper, never()).insert(any(TaskEntity.class));
        verify(analysisTaskEventOutbox, never()).append(any(), any(), anyInt());
        verify(analysisTaskMessagePublisher, never()).publish(any());
        verify(aiTaskQuotaService, never()).reserve(any());
    }

    @Test
    void vesselTaskRejectsFailedQualityForOrdinaryUser() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.VESSEL_SEGMENTATION, 3);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L)
                .status(ImageStatus.UPLOADED).qualityStatus(ImageQualityStatus.FAIL).build());

        assertThatThrownBy(() -> taskService.createTask(dto, 7))
                .isInstanceOf(BaseException.class)
                .extracting("code").isEqualTo(ErrorMessageSignal.CONFLICT);
        verify(analysisTaskEventOutbox, never()).append(any(), any(), anyInt());
        verify(analysisTaskMessagePublisher, never()).publish(any());
    }

    @Test
    void doctorMayOverrideFailedQualityWithReason() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.VESSEL_SEGMENTATION, 3);
        dto.setQualityOverride(true);
        dto.setQualityOverrideReason("临床紧急，接受低质量风险");
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L)
                .status(ImageStatus.UPLOADED).qualityStatus(ImageQualityStatus.FAIL).build());
        when(userRegisterMapper.selectById(7)).thenReturn(user(7, UserRole.DOCTOR));
        when(taskMapper.countUnfinishedTask(20L, TaskType.VESSEL_SEGMENTATION)).thenReturn(0L);
        doAnswer(invocation -> { ((TaskEntity) invocation.getArgument(0)).setId(100L); return 1; })
                .when(taskMapper).insert(any(TaskEntity.class));

        taskService.createTask(dto, 7);

        verify(analysisTaskEventOutbox).append(any(), any(), anyInt());
        verifyNoInteractions(analysisTaskMessagePublisher);
    }

    @Test
    void qualityTaskDoesNotReserveGpuQuota() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.IMAGE_QUALITY_CHECK, 3);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L)
                .status(ImageStatus.UPLOADED).qualityStatus(ImageQualityStatus.NOT_CHECKED).build());
        when(taskMapper.countUnfinishedTask(20L, TaskType.IMAGE_QUALITY_CHECK)).thenReturn(0L);
        doAnswer(invocation -> { ((TaskEntity) invocation.getArgument(0)).setId(100L); return 1; })
                .when(taskMapper).insert(any(TaskEntity.class));

        taskService.createTask(dto, 7);

        verify(aiTaskQuotaService, never()).reserve(any());
    }

    private UserEntity user(int id, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setRoleCode(role);
        return user;
    }

    @Test
    void createTaskPropagatesOutboxFailureAndReleasesQuotaOnRollback() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.VESSEL_SEGMENTATION, 5);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L)
                .status(ImageStatus.UPLOADED).qualityStatus(ImageQualityStatus.PASS).build());
        when(taskMapper.countUnfinishedTask(20L, TaskType.VESSEL_SEGMENTATION)).thenReturn(0L);
        doAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(taskMapper).insert(any(TaskEntity.class));
        IllegalStateException outboxFailure = new IllegalStateException("outbox unavailable");
        doThrow(outboxFailure).when(analysisTaskEventOutbox).append(any(), any(), anyInt());

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> taskService.createTask(dto, 7))
                    .isSameAs(outboxFailure);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(logMapper, never()).insert(any(LogEntity.class));
        verify(analysisTaskMessagePublisher, never()).publish(any());
        verify(aiTaskQuotaService).release(reservation);
    }

    @Test
    void retryTaskIsRejectedWhenAiQuotaIsExhausted() {
        TaskEntity task = TaskEntity.builder()
                .id(100L)
                .taskNo("TASK-100")
                .caseId(10L)
                .imageFileId(20L)
                .taskType(TaskType.VESSEL_SEGMENTATION)
                .status(TaskStatus.FAILED)
                .priority(5)
                .retryCount(0)
                .maxRetryCount(3)
                .submittedBy(7)
                .build();
        when(taskMapper.selectById(100)).thenReturn(task);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L)
                .status(ImageStatus.UPLOADED).qualityStatus(ImageQualityStatus.PASS).build());
        when(aiTaskQuotaService.reserve(7)).thenThrow(new RateLimitException("quota", 10));

        assertThatThrownBy(() -> taskService.retryTask(100, 7))
                .isInstanceOf(RateLimitException.class);

        verify(aiTaskQuotaService).reserve(7);
        verify(analysisTaskMessagePublisher, never()).publish(any());
    }

    @Test
    void transactionRollbackReleasesReservedQuota() {
        CreateTaskDTO dto = new CreateTaskDTO(10L, 20L, TaskType.VESSEL_SEGMENTATION, 5);
        when(caseMapper.selectById(10L)).thenReturn(CaseEntity.builder().id(10).status(CaseStatus.ACTIVE).build());
        when(imageMapper.selectById(20L)).thenReturn(ImageFileEntity.builder().id(20L).caseId(10L)
                .status(ImageStatus.UPLOADED).qualityStatus(ImageQualityStatus.PASS).build());
        when(taskMapper.countUnfinishedTask(20L, TaskType.VESSEL_SEGMENTATION)).thenReturn(0L);
        doAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(taskMapper).insert(any(TaskEntity.class));

        TransactionSynchronizationManager.initSynchronization();
        try {
            taskService.createTask(dto, 7);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(aiTaskQuotaService).release(reservation);
    }
}
