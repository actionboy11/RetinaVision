package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.config.DoctorReviewReminderProperties;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.mapper.AnalysisReportMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.DoctorReviewReminderItemVO;
import com.example.retinavision.pojo.VO.DoctorReviewReminderVO;
import com.example.retinavision.service.DoctorReviewReminderService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 医生审核提醒实时统计服务。
 *
 * <p>注意：这个服务不是后台定时任务，不会写入数据库。
 * 前端每隔一段时间请求一次提醒接口时，后端现场根据 analysis_task、
 * analysis_result、analysis_review、analysis_report 计算当前医生待办。</p>
 *
 * <p>为什么从 analysis_task 出发？
 * 因为“还没有审核记录”的任务也需要提醒。如果从 analysis_review 出发，
 * 这类最需要提醒的任务反而查不到。</p>
 */
@Service
public class DoctorReviewReminderServiceImpl implements DoctorReviewReminderService {

    private static final int MAX_OVERDUE_ITEMS = 5;

    private final TaskMapper taskMapper;
    private final AnalysisResultMapper resultMapper;
    private final AnalysisReviewMapper reviewMapper;
    private final AnalysisReportMapper reportMapper;
    private final CaseMapper caseMapper;
    private final ImageMapper imageMapper;
    private final DoctorReviewReminderProperties properties;

    public DoctorReviewReminderServiceImpl(TaskMapper taskMapper,
                                           AnalysisResultMapper resultMapper,
                                           AnalysisReviewMapper reviewMapper,
                                           AnalysisReportMapper reportMapper,
                                           CaseMapper caseMapper,
                                           ImageMapper imageMapper,
                                           DoctorReviewReminderProperties properties) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.reviewMapper = reviewMapper;
        this.reportMapper = reportMapper;
        this.caseMapper = caseMapper;
        this.imageMapper = imageMapper;
        this.properties = properties;
    }

    @Override
    public DoctorReviewReminderVO getReminderSummary() {
        long thresholdMinutes = properties.getOverdueThresholdMinutes();
        LocalDateTime now = LocalDateTime.now();
        DoctorReviewReminderVO summary = new DoctorReviewReminderVO();
        summary.setOverdueThresholdMinutes(thresholdMinutes);

        // 只统计“已经成功完成的血管分割任务”，因为只有它们才会进入医生审核和报告签发流程。
        List<TaskEntity> completedVesselTasks = taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getStatus, TaskStatus.SUCCESS)
                .eq(TaskEntity::getTaskType, TaskType.VESSEL_SEGMENTATION)
                .isNotNull(TaskEntity::getFinishedAt)
                .orderByDesc(TaskEntity::getFinishedAt));

        for (TaskEntity task : completedVesselTasks) {
            AnalysisResultEntity result = findResult(task.getId());
            if (result == null) {
                continue;
            }

            AnalysisReviewEntity review = findReview(result.getId());
            boolean overdue = isOverdue(task.getFinishedAt(), now, thresholdMinutes);

            // 待审核：没有审核记录，或者医生还标记为 PENDING / CHANGES_REQUESTED。
            if (isPendingReview(review)) {
                summary.setPendingReviewCount(summary.getPendingReviewCount() + 1);
                if (overdue) {
                    summary.setOverdueReviewCount(summary.getOverdueReviewCount() + 1);
                    addOverdueItem(summary, task, result, "待医生审核");
                }
                continue;
            }

            // 待签发：医生已经 APPROVED，但还没有 SIGNED 报告。
            if (isPendingReport(review, result.getId())) {
                summary.setPendingReportCount(summary.getPendingReportCount() + 1);
                if (overdue) {
                    summary.setOverdueReportCount(summary.getOverdueReportCount() + 1);
                    addOverdueItem(summary, task, result, "待签发报告");
                }
            }
        }

        summary.getLatestOverdueItems().sort(Comparator.comparing(DoctorReviewReminderItemVO::getFinishedAt));
        if (summary.getLatestOverdueItems().size() > MAX_OVERDUE_ITEMS) {
            summary.setLatestOverdueItems(summary.getLatestOverdueItems().subList(0, MAX_OVERDUE_ITEMS));
        }
        return summary;
    }

    private AnalysisResultEntity findResult(Long taskId) {
        return resultMapper.selectOne(new LambdaQueryWrapper<AnalysisResultEntity>()
                .eq(AnalysisResultEntity::getTaskId, taskId));
    }

    private AnalysisReviewEntity findReview(Long resultId) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<AnalysisReviewEntity>()
                .eq(AnalysisReviewEntity::getResultId, resultId));
    }

    private boolean isPendingReview(AnalysisReviewEntity review) {
        return review == null
                || review.getStatus() == ReviewStatus.PENDING
                || review.getStatus() == ReviewStatus.CHANGES_REQUESTED;
    }

    private boolean isPendingReport(AnalysisReviewEntity review, Long resultId) {
        if (review == null || review.getStatus() != ReviewStatus.APPROVED) {
            return false;
        }
        return !hasSignedReport(resultId);
    }

    private boolean hasSignedReport(Long resultId) {
        Long signedCount = reportMapper.selectCount(new LambdaQueryWrapper<AnalysisReportEntity>()
                .eq(AnalysisReportEntity::getResultId, resultId)
                .eq(AnalysisReportEntity::getStatus, ReportStatus.SIGNED));
        return signedCount != null && signedCount > 0;
    }

    /**
     * 超时基准使用任务完成时间 finishedAt。
     *
     * <p>医生是否“晚处理”，应该从 AI 结果已经可用的时刻开始计算，
     * 而不是从病例创建、任务提交或报告草稿创建时间开始计算。</p>
     */
    private boolean isOverdue(LocalDateTime finishedAt, LocalDateTime now, long thresholdMinutes) {
        return finishedAt != null && Duration.between(finishedAt, now).toMinutes() >= thresholdMinutes;
    }

    private void addOverdueItem(DoctorReviewReminderVO summary,
                                TaskEntity task,
                                AnalysisResultEntity result,
                                String reason) {
        DoctorReviewReminderItemVO item = new DoctorReviewReminderItemVO();
        item.setTaskId(task.getId());
        item.setResultId(result.getId());
        item.setFinishedAt(task.getFinishedAt());
        item.setReason(reason);

        CaseEntity medicalCase = task.getCaseId() == null ? null : caseMapper.selectById(task.getCaseId());
        ImageFileEntity image = task.getImageFileId() == null ? null : imageMapper.selectById(task.getImageFileId());
        item.setCaseNo(medicalCase == null ? null : medicalCase.getCaseNo());
        item.setOriginalFilename(image == null ? null : image.getOriginalFilename());

        summary.getLatestOverdueItems().add(item);
    }
}
