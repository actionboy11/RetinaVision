package com.example.retinavision.agent.evaluation;

import com.example.retinavision.agent.*;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.enumeration.*;
import com.example.retinavision.pojo.VO.*;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.DoctorAgentQueryService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FixtureDoctorAgentQueryService implements DoctorAgentQueryService {
    private static final int MAX_PAGE_SIZE = 10;
    private final List<DoctorAgentCaseSummaryVO> cases = createCases();
    private final List<DoctorAgentTaskSummaryVO> tasks = createTasks();
    private final List<DoctorClinicalQueueItemVO> queue = createQueue();

    @Override
    public DoctorWorkloadVO getClinicalWorkload(CurrentUserVO doctor) {
        requireDoctor(doctor);
        return new DoctorWorkloadVO(12, 12, 3, 5, 2, 1);
    }

    @Override
    public PageResult<DoctorAgentCaseSummaryVO> searchAssignedCases(DoctorCaseSearchCriteria criteria,
                                                                    Integer page, Integer pageSize,
                                                                    CurrentUserVO doctor) {
        requireDoctor(doctor);
        List<DoctorAgentCaseSummaryVO> filtered = cases.stream().filter(item -> matches(criteria, item)).toList();
        return page(filtered, page, pageSize);
    }

    @Override
    public PageResult<DoctorAgentTaskSummaryVO> searchTasks(DoctorTaskSearchCriteria criteria, Integer page,
                                                            Integer pageSize, CurrentUserVO doctor) {
        requireDoctor(doctor);
        List<DoctorAgentTaskSummaryVO> filtered = tasks.stream()
                .filter(item -> criteria.taskType() == item.getTaskType())
                .filter(item -> matches(criteria.status(), item.getStatus())).toList();
        return page(filtered, page, pageSize);
    }

    @Override
    public DoctorAgentTaskDetailVO getTaskDetail(String taskReference, CurrentUserVO doctor) {
        requireDoctor(doctor);
        DoctorAgentTaskSummaryVO found = tasks.stream()
                .filter(item -> item.getTaskNo().equalsIgnoreCase(taskReference)
                        || String.valueOf(item.getTaskId()).equals(taskReference))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("评测任务不存在"));
        DoctorAgentTaskDetailVO detail = new DoctorAgentTaskDetailVO();
        detail.setTaskId(found.getTaskId());
        detail.setTaskNo(found.getTaskNo());
        detail.setCaseId(found.getCaseId());
        detail.setCaseNo(found.getCaseNo());
        detail.setPatientNo(found.getPatientNo());
        detail.setTaskType(found.getTaskType());
        detail.setStatus(found.getStatus());
        detail.setUpdatedAt(found.getUpdatedAt());
        detail.setLogs(List.of());
        return detail;
    }

    @Override
    public PageResult<DoctorClinicalQueueItemVO> searchClinicalQueue(DoctorClinicalQueueCriteria criteria,
                                                                     Integer page, Integer pageSize,
                                                                     CurrentUserVO doctor) {
        requireDoctor(doctor);
        List<DoctorClinicalQueueItemVO> filtered = queue.stream().filter(item ->
                criteria.queueType() == DoctorClinicalQueueType.PENDING_REVIEW
                        ? item.getReviewStatus() == ReviewStatus.PENDING
                        : item.getReviewStatus() == ReviewStatus.APPROVED
                        && item.getReportStatus() != ReportStatus.SIGNED).toList();
        return page(filtered, page, pageSize);
    }

    private boolean matches(DoctorCaseSearchCriteria criteria, DoctorAgentCaseSummaryVO item) {
        if (criteria.eyeSide() != null && criteria.eyeSide() != item.getEyeSide()) return false;
        if (criteria.workflowStatus() != null && criteria.workflowStatus() != item.getWorkflowStatus()) return false;
        return switch (criteria.segmentationState()) {
            case ANY -> true;
            case NOT_CREATED -> item.getSegmentationStatus() == null;
            case NOT_COMPLETED -> item.getSegmentationStatus() != TaskStatus.SUCCESS;
            case IN_PROGRESS -> item.getSegmentationStatus() == TaskStatus.RUNNING
                    || item.getSegmentationStatus() == TaskStatus.WAITING;
            case FAILED -> item.getSegmentationStatus() == TaskStatus.FAILED;
            case SUCCESS -> item.getSegmentationStatus() == TaskStatus.SUCCESS;
        };
    }

    private boolean matches(DoctorTaskStatusFilter filter, TaskStatus status) {
        return switch (filter) {
            case ANY -> true;
            case WAITING -> status == TaskStatus.CREATED || status == TaskStatus.WAITING
                    || status == TaskStatus.RETRYING;
            case RUNNING -> status == TaskStatus.RUNNING;
            case FAILED -> status == TaskStatus.FAILED;
            case SUCCESS -> status == TaskStatus.SUCCESS;
        };
    }

    private <T> PageResult<T> page(List<T> values, Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null || requestedPage < 1 ? 1 : requestedPage;
        int size = Math.min(MAX_PAGE_SIZE, requestedSize == null || requestedSize < 1 ? MAX_PAGE_SIZE : requestedSize);
        int from = Math.min(values.size(), (page - 1) * size);
        int to = Math.min(values.size(), from + size);
        return new PageResult<>(new ArrayList<>(values.subList(from, to)), values.size(), page, size);
    }

    private void requireDoctor(CurrentUserVO user) {
        if (user == null || user.getRoleCode() != UserRole.DOCTOR) {
            throw new IllegalArgumentException("评测医生身份无效");
        }
    }

    private List<DoctorAgentCaseSummaryVO> createCases() {
        List<DoctorAgentCaseSummaryVO> values = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            DoctorAgentCaseSummaryVO item = new DoctorAgentCaseSummaryVO();
            item.setCaseId(10_000 + index);
            item.setCaseNo("EVAL-C-%03d".formatted(index));
            item.setPatientId(20_000L + index);
            item.setPatientNo("PT-EVAL-%03d".formatted(index));
            item.setPatientAge(30 + index);
            item.setPatientGender(index % 2 == 0 ? PatientGender.FEMALE : PatientGender.MALE);
            item.setEyeSide(index % 2 == 0 ? EyeSide.RIGHT : EyeSide.LEFT);
            item.setWorkflowStatus(CaseWorkflowStatus.IN_REVIEW);
            item.setSegmentationStatus(index <= 3 ? null : index <= 5 ? TaskStatus.FAILED : TaskStatus.SUCCESS);
            item.setUpdatedAt(LocalDateTime.of(2026, 10, 7, 12, 0).minusMinutes(index));
            values.add(item);
        }
        return List.copyOf(values);
    }

    private List<DoctorAgentTaskSummaryVO> createTasks() {
        List<DoctorAgentTaskSummaryVO> values = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            DoctorAgentTaskSummaryVO item = new DoctorAgentTaskSummaryVO();
            item.setTaskId(30_000L + index);
            item.setTaskNo("EVAL-TASK-%03d".formatted(index));
            item.setCaseId(10_000 + index);
            item.setCaseNo("EVAL-C-%03d".formatted(index));
            item.setPatientNo("PT-EVAL-%03d".formatted(index));
            item.setTaskType(AnalysisTaskType.VESSEL_SEGMENTATION);
            item.setStatus(index <= 2 ? TaskStatus.FAILED : TaskStatus.SUCCESS);
            item.setUpdatedAt(LocalDateTime.of(2026, 10, 7, 11, 0).minusMinutes(index));
            values.add(item);
        }
        return List.copyOf(values);
    }

    private List<DoctorClinicalQueueItemVO> createQueue() {
        List<DoctorClinicalQueueItemVO> values = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            DoctorClinicalQueueItemVO item = new DoctorClinicalQueueItemVO();
            item.setTaskId(30_000L + index);
            item.setTaskNo("EVAL-TASK-%03d".formatted(index));
            item.setCaseId(10_000 + index);
            item.setCaseNo("EVAL-C-%03d".formatted(index));
            item.setPatientNo("PT-EVAL-%03d".formatted(index));
            item.setReviewStatus(index <= 2 ? ReviewStatus.PENDING : ReviewStatus.APPROVED);
            item.setReportStatus(ReportStatus.DRAFT);
            values.add(item);
        }
        return List.copyOf(values);
    }
}
