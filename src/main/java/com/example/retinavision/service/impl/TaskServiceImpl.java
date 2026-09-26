package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.retinavision.analysis.application.model.AnalysisTaskRequestedEvent;
import com.example.retinavision.analysis.application.port.out.AnalysisTaskEventOutbox;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.common.Deletable;
import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.CaseWorkflowStatus;
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
import com.example.retinavision.mq.AnalysisTaskMessage;
import com.example.retinavision.mq.AnalysisTaskMessagePublisher;
import com.example.retinavision.pojo.DTO.CreateTaskDTO;
import com.example.retinavision.pojo.DTO.TaskListQueryDTO;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.LogEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.pojo.VO.CreateTaskVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.RetryTaskVO;
import com.example.retinavision.pojo.VO.TaskDetailVO;
import com.example.retinavision.pojo.VO.TaskListItemVO;
import com.example.retinavision.pojo.VO.TaskLogVO;
import com.example.retinavision.pojo.VO.ImageQualitySummaryVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.TaskService;
import com.example.retinavision.service.AiTaskQuotaService;
import com.example.retinavision.service.AiQuotaReservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TaskServiceImpl implements TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    private final TaskMapper taskMapper;
    private final ImageMapper imageMapper;
    private final CaseMapper caseMapper;
    private final UserRegisterMapper userRegisterMapper;
    private final LogMapper logMapper;
    private final AnalysisTaskMessagePublisher analysisTaskMessagePublisher;
    private final AnalysisTaskEventOutbox analysisTaskEventOutbox;
    private final AiTaskQuotaService aiTaskQuotaService;

    public TaskServiceImpl(TaskMapper taskMapper,
                           ImageMapper imageMapper,
                           CaseMapper caseMapper,
                           UserRegisterMapper userRegisterMapper,
                           LogMapper logMapper,
                           AnalysisTaskMessagePublisher analysisTaskMessagePublisher,
                           AnalysisTaskEventOutbox analysisTaskEventOutbox,
                           AiTaskQuotaService aiTaskQuotaService) {
        this.taskMapper = taskMapper;
        this.imageMapper = imageMapper;
        this.caseMapper = caseMapper;
        this.userRegisterMapper = userRegisterMapper;
        this.logMapper = logMapper;
        this.analysisTaskMessagePublisher = analysisTaskMessagePublisher;
        this.analysisTaskEventOutbox = analysisTaskEventOutbox;
        this.aiTaskQuotaService = aiTaskQuotaService;
    }

    // 1. 获取任务列表，支持分页、关键字搜索和病例号过滤
    @Override
    public PageResult<TaskListItemVO> getLTaskList(TaskListQueryDTO taskListQueryDTO, CurrentUserVO user) {
        TaskListQueryDTO safeQuery = taskListQueryDTO == null ? new TaskListQueryDTO() : taskListQueryDTO;
        int pageNo = normalizePageNo(safeQuery.getPageNo());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        int offset = (pageNo - 1) * pageSize;

        safeQuery.setKeyword(normalizeKeyword(safeQuery.getKeyword()));
        safeQuery.setCaseNo(normalizeKeyword(safeQuery.getCaseNo()));

        Integer creatorId = user.getRoleCode() == UserRole.USER || user.getRoleCode() == UserRole.RESEARCHER
                ? user.getId() : null;
        Integer doctorId = user.getRoleCode() == UserRole.DOCTOR ? user.getId() : null;
        Long total = taskMapper.countTaskPage(safeQuery, creatorId, doctorId);
        List<TaskListItemVO> list = taskMapper.selectTaskPage(
                safeQuery, creatorId, doctorId, offset, pageSize);
        return new PageResult<>(list, total, pageNo, pageSize);
    }

    @Override
    @Transactional
    public CreateTaskVO createTask(CreateTaskDTO createTaskDTO, Integer submittedBy) {
        // 参数校验
        validateCreateTaskRequest(createTaskDTO);
        Long caseId = createTaskDTO.getCaseId();
        Long imageFileId = createTaskDTO.getImageFileId();
        CaseEntity caseEntity = caseMapper.selectById(caseId);
        ImageFileEntity imageFileEntity = imageMapper.selectById(imageFileId);
        // 校验病例和图像的有效性和关联性，确保它们存在且未被删除，并且图像确实属于该病例。同时还检查病例状态是否允许创建任务。
        validateCaseAndImageForTask(caseEntity, imageFileEntity, caseId);
        // 同一图像同一任务类型的任务只能有一个未完成的，避免重复提交导致资源浪费和结果冲突。
        Long unfinishedTaskCount = taskMapper.countUnfinishedTask(imageFileId, createTaskDTO.getTaskType());
        if (unfinishedTaskCount != null && unfinishedTaskCount > 0) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, ErrorMessageContant.CONFLICT_MSG);
        }
        // 预留 AI 任务配额，确保用户在提交任务时不会超过系统设定的配额限制。
        AiQuotaReservation reservation = createTaskDTO.getTaskType() == TaskType.VESSEL_SEGMENTATION
                ? aiTaskQuotaService.reserve(submittedBy)
                : null;
        // 注册回滚回调，确保在任务创建失败时释放预留的配额。
        boolean rollbackCallbackRegistered = reservation != null && registerQuotaRollback(reservation);
        try {
        LocalDateTime now = LocalDateTime.now();
        TaskEntity taskEntity = TaskEntity.builder()
                .taskNo(generateTaskNo())
                .caseId(caseId)
                .imageFileId(imageFileId)
                .taskType(createTaskDTO.getTaskType())
                .status(TaskStatus.WAITING)
                .priority(createTaskDTO.getPriority())
                .retryCount(0)
                .maxRetryCount(DEFAULT_MAX_RETRY_COUNT)
                .errorMessage(null)
                .qualityOverride(false)
                .qualityOverrideReason(null)
                .submittedBy(submittedBy)
                .submittedAt(now)
                .updatedAt(now)
                .build();
        taskMapper.insert(taskEntity);
        if (taskEntity.getTaskType() == TaskType.VESSEL_SEGMENTATION
                && caseEntity.getWorkflowStatus() != CaseWorkflowStatus.COMPLETED) {
            caseEntity.setWorkflowStatus(CaseWorkflowStatus.IN_REVIEW);
            caseEntity.setUpdatedAt(now);
            caseMapper.updateById(caseEntity);
        }
        analysisTaskEventOutbox.append(
                new AnalysisTaskRequestedEvent(
                        taskEntity.getId(),
                        taskEntity.getTaskNo(),
                        taskEntity.getCaseId(),
                        taskEntity.getImageFileId(),
                        AnalysisTaskType.valueOf(taskEntity.getTaskType().name()),
                        taskEntity.getPriority(),
                        taskEntity.getSubmittedBy(),
                        taskEntity.getSubmittedAt()),
                "ANALYSIS_TASK_REQUESTED",
                1);
        if (taskEntity.getTaskType() == TaskType.IMAGE_QUALITY_CHECK) {
            imageFileEntity.setQualityStatus(ImageQualityStatus.CHECKING);
            imageFileEntity.setQualityTaskId(taskEntity.getId());
            imageFileEntity.setUpdatedAt(now);
            imageMapper.updateById(imageFileEntity);
        }
        String creationAuditMessage = buildCreationAuditMessage(taskEntity, imageFileEntity);
        insertTaskLog(
                taskEntity.getId(),
                null,
                TaskStatus.WAITING,
                creationAuditMessage,
                "USER",
                submittedBy,
                now);

        return CreateTaskVO.builder()
                .id(taskEntity.getId())
                .taskNo(taskEntity.getTaskNo())
                .taskStatus(TaskStatus.WAITING)
                .errorMessage(null)
                .build();
        } catch (RuntimeException exception) {
            if (reservation != null && !rollbackCallbackRegistered) {
                releaseQuotaSafely(reservation);
            }
            throw exception;
        }
    }

    @Override
    public TaskDetailVO getTaskDetail(Integer taskId, Integer submittedBy) {
        TaskEntity taskEntity = getTaskOrThrow(taskId);

        TaskDetailVO taskDetailVO = TaskDetailVO.builder().build();
        BeanUtils.copyProperties(taskEntity, taskDetailVO);
        taskDetailVO.setSubmittedBy(taskEntity.getSubmittedBy() == null ? null : Long.valueOf(taskEntity.getSubmittedBy()));

        CaseEntity caseEntity = caseMapper.selectById(taskEntity.getCaseId());
        ImageFileEntity imageFileEntity = imageMapper.selectById(taskEntity.getImageFileId());
        UserEntity userEntity = userRegisterMapper.selectById(taskEntity.getSubmittedBy());
        if (caseEntity != null) {
            taskDetailVO.setCaseNo(caseEntity.getCaseNo());
        }
        if (imageFileEntity != null) {
            taskDetailVO.setImagePreviewUrl(imageFileEntity.getPreviewUrl());
            taskDetailVO.setOriginalFilename(imageFileEntity.getOriginalFilename());
            taskDetailVO.setQualitySummary(new ImageQualitySummaryVO(imageFileEntity.getQualityStatus(),
                    imageFileEntity.getQualityScore(), imageFileEntity.getQualityResultId(),
                    imageFileEntity.getQualityTaskId(), imageFileEntity.getQualityCheckedAt()));
        }
        if (userEntity != null) {
            taskDetailVO.setSubmittedByName(userEntity.getRealName());
        }
        return taskDetailVO;
    }

    @Override
    @Transactional
    public void cancelTask(Integer taskId, Integer id) {
        TaskEntity taskEntity = getTaskOrThrow(taskId);

        // TODO 进阶版本：如果要支持取消 RUNNING 任务，不能只把数据库状态改成 CANCELED。
        // 需要新增 Worker 协作取消机制，例如 CANCEL_REQUESTED 状态、Worker 定期检查状态并主动停止任务。
        if (taskEntity.getStatus() != TaskStatus.CREATED && taskEntity.getStatus() != TaskStatus.WAITING) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "任务状态不允许取消");
        }

        if (!taskEntity.getSubmittedBy().equals(id)) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "您没有权限取消该任务");
        }

        LocalDateTime now = LocalDateTime.now();
        TaskStatus fromStatus = taskEntity.getStatus();
        taskEntity.setStatus(TaskStatus.CANCELED);
        taskEntity.setCanceledAt(now);
        taskEntity.setUpdatedAt(now);
        taskMapper.updateById(taskEntity);
        insertTaskLog(taskEntity.getId(), fromStatus, TaskStatus.CANCELED, "任务已取消", "USER", id, now);
    }

    @Override
    @Transactional  // 确保在重试任务时，数据库操作和 AI 配额操作是原子性的。
    public RetryTaskVO retryTask(Integer taskId, Integer id) {
        TaskEntity taskEntity = getTaskOrThrow(taskId);
        if (taskEntity.getStatus() != TaskStatus.FAILED) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "只有失败的任务才能重试");
        }
        if (taskEntity.getRetryCount() >= taskEntity.getMaxRetryCount()) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "任务重试次数已达上限");
        }

        CaseEntity caseEntity = caseMapper.selectById(taskEntity.getCaseId());
        ImageFileEntity imageFileEntity = imageMapper.selectById(taskEntity.getImageFileId());
        validateCaseAndImageForRetry(caseEntity, imageFileEntity);

        AiQuotaReservation reservation = taskEntity.getTaskType() == TaskType.VESSEL_SEGMENTATION
                ? aiTaskQuotaService.reserve(id)
                : null;
        //如果 Redis 已经把 AI 配额加了 1，但数据库任务创建或 MQ 投递失败，需要把刚才占用的配额退回去，
        // 否则用户会损失一次宝贵的配额机会。registerQuotaRollback() 方法注册了一个事务回滚回调，
        // 在事务完成后检查事务状态，如果不是提交成功，则调用 releaseQuotaSafely() 方法释放配额。
        boolean rollbackCallbackRegistered = reservation != null && registerQuotaRollback(reservation);
        try {
        LocalDateTime now = LocalDateTime.now();
        int nextRetryCount = taskEntity.getRetryCount() + 1;
        taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, taskEntity.getId())
                .set(TaskEntity::getRetryCount, nextRetryCount)
                .set(TaskEntity::getStatus, TaskStatus.WAITING)
                .set(TaskEntity::getErrorMessage, null)
                .set(TaskEntity::getStartedAt, null)
                .set(TaskEntity::getFinishedAt, null)
                .set(TaskEntity::getUpdatedAt, now));
        taskEntity.setRetryCount(nextRetryCount);
        taskEntity.setStatus(TaskStatus.WAITING);
        taskEntity.setErrorMessage(null);
        taskEntity.setStartedAt(null);
        taskEntity.setFinishedAt(null);
        taskEntity.setUpdatedAt(now);

        analysisTaskMessagePublisher.publish(buildTaskMessage(taskEntity));
        insertTaskLog(taskEntity.getId(), TaskStatus.FAILED, TaskStatus.WAITING, "任务重试，已重新投递 MQ", "USER", id, now);

        return RetryTaskVO.builder()
                .id(taskEntity.getId())
                .taskNo(taskEntity.getTaskNo())
                .taskStatus(taskEntity.getStatus())
                .retryCount(taskEntity.getRetryCount())
                .build();
        } catch (RuntimeException exception) {
            if (reservation != null && !rollbackCallbackRegistered) {
                releaseQuotaSafely(reservation);
            }
            throw exception;
        }
    }

    @Override
    public List<TaskLogVO> getTaskLog(Integer taskId, Integer id) {
        getTaskOrThrow(taskId);
        return logMapper.selectByTaskId(taskId);
    }

    private void validateCreateTaskRequest(CreateTaskDTO createTaskDTO) {
        if (createTaskDTO == null
                || createTaskDTO.getCaseId() == null
                || createTaskDTO.getImageFileId() == null
                || createTaskDTO.getTaskType() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.PARAM_ERROR_MSG);
        }
    }

    private void validateCaseAndImageForTask(CaseEntity caseEntity, ImageFileEntity imageFileEntity, Long caseId) {
        assertNotNullAndNotDeleted(caseEntity, "病例");
        assertNotNullAndNotDeleted(imageFileEntity, "图像");
        if (!imageFileEntity.getCaseId().equals(caseId)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "图片所属病例与任务不一致");
        }
        if (caseEntity.getStatus() != CaseStatus.ACTIVE) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "病例不是活跃状态，不允许创建任务");
        }
        if (imageFileEntity.getStatus() == ImageStatus.DELETED) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "图像已删除，不允许创建任务");
        }
    }

    private String buildCreationAuditMessage(TaskEntity task, ImageFileEntity image) {
        if (task.getTaskType() != TaskType.VESSEL_SEGMENTATION) {
            return "任务已创建并等待消息发布";
        }
        ImageQualityStatus qualityStatus = image.getQualityStatus() == null
                ? ImageQualityStatus.NOT_CHECKED : image.getQualityStatus();
        String qualityScore = image.getQualityScore() == null
                ? "无有效评分" : image.getQualityScore().toString();
        return "医生参考图像质量 " + qualityStatus.name() + " / " + qualityScore
                + " 后创建任务；任务已创建并等待消息发布";
    }

    private void validateCaseAndImageForRetry(CaseEntity caseEntity, ImageFileEntity imageFileEntity) {
        if (caseEntity == null || imageFileEntity == null
                || caseEntity.getDeletedAt() != null
                || imageFileEntity.getDeletedAt() != null
                || imageFileEntity.getStatus() == ImageStatus.DELETED) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "任务所属病例或图像不存在或已删除");
        }
    }

    private TaskEntity getTaskOrThrow(Integer taskId) {
        TaskEntity taskEntity = taskMapper.selectById(taskId);
        if (taskEntity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.TASK_NOT_EXISTS);
        }
        return taskEntity;
    }
    private boolean registerQuotaRollback(AiQuotaReservation reservation) {
        // 注册事务回滚回调，确保在任务创建失败时释放预留的配额。
        // TransactionSynchronizationManager.isSynchronizationActive() 方法检查当前线程是否处于事务同步状态，
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        //注册事务回调
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    // 如果事务没有提交成功，则调用 releaseQuotaSafely() 方法释放预留的配额。
                    releaseQuotaSafely(reservation);
                }
            }
        });
        return true;
    }

    private void releaseQuotaSafely(AiQuotaReservation reservation) {
        try {
            // 释放 AI 任务配额，减少分钟和日计数，如果计数为 0，则删除键，否则减少计数并返回 1 表示成功
            aiTaskQuotaService.release(reservation);
        } catch (RuntimeException compensationFailure) {
            // 配额补偿失败只能记录，不能覆盖任务创建或 MQ 投递的原始异常。
            log.error("AI quota compensation failed minuteKey={} dayKey={}",
                    reservation.minuteKey(), reservation.dayKey(), compensationFailure);
        }
    }

    private AnalysisTaskMessage buildTaskMessage(TaskEntity taskEntity) {
        return AnalysisTaskMessage.builder()
                .taskId(taskEntity.getId())
                .taskNo(taskEntity.getTaskNo())
                .caseId(taskEntity.getCaseId())
                .imageFileId(taskEntity.getImageFileId())
                .taskType(taskEntity.getTaskType())
                .priority(taskEntity.getPriority())
                .submittedBy(taskEntity.getSubmittedBy())
                .submittedAt(taskEntity.getSubmittedAt())
                .build();
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private int normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return DEFAULT_PAGE_NO;
        }
        return pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String generateTaskNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "T" + date + random;
    }

    private void insertTaskLog(Long taskId,
                               TaskStatus fromStatus,
                               TaskStatus toStatus,
                               String message,
                               String operatorType,
                               Integer operatorId,
                               LocalDateTime createdAt) {
        logMapper.insert(LogEntity.builder()
                .taskId(taskId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .message(message)
                .operatorType(operatorType)
                .operatorId(operatorId)
                .createdAt(createdAt)
                .build());
    }

    private <T extends Deletable> void assertNotNullAndNotDeleted(T entity, String entityName) {
        if (entity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, entityName + "不存在");
        }
        if (entity.getDeletedAt() != null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, entityName + "已被删除");
        }
    }
}
