package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.analysis.domain.model.AnalysisTaskType;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.VO.CaseAnalysisTimelineVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import com.example.retinavision.service.AgentClinicalReferenceService;
import com.example.retinavision.service.CaseAnalysisTimelineService;
import com.example.retinavision.service.DoctorAgentQueryService;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DoctorAgentSkillOrchestrator {
    private static final double MIN_ROUTE_CONFIDENCE = 0.65;
    private final AgentSkillRouter router;
    private final DoctorAgentQueryService queries;
    private final AgentQueryContextService contexts;
    private final AgentClinicalReferenceService references;
    private final CaseAnalysisTimelineService timelines;
    private final AgentSkillVersionResolver versions;
    private final AgentSkillRegistry registry;
    private final AgentSkillCatalogService catalog;
    private final AgentContextCommandParser commandParser;
    private final AgentUnsafeRequestPolicy unsafeRequestPolicy;

    @Autowired
    public DoctorAgentSkillOrchestrator(AgentSkillRouter router, DoctorAgentQueryService queries,
                                        AgentQueryContextService contexts, AgentClinicalReferenceService references,
                                        CaseAnalysisTimelineService timelines,
                                        AgentSkillVersionResolver versions, AgentSkillRegistry registry,
                                        AgentSkillCatalogService catalog, AgentContextCommandParser commandParser,
                                        AgentUnsafeRequestPolicy unsafeRequestPolicy) {
        this.router = router;
        this.queries = queries;
        this.contexts = contexts;
        this.references = references;
        this.timelines = timelines;
        this.versions = versions;
        this.registry = registry;
        this.catalog = catalog;
        this.commandParser = commandParser;
        this.unsafeRequestPolicy = unsafeRequestPolicy;
    }

    public DoctorAgentSkillOrchestrator(AgentSkillRouter router, DoctorAgentQueryService queries,
                                        AgentQueryContextService contexts, AgentClinicalReferenceService references,
                                        CaseAnalysisTimelineService timelines,
                                        AgentSkillVersionResolver versions) {
        this.router = router;
        this.queries = queries;
        this.contexts = contexts;
        this.references = references;
        this.timelines = timelines;
        this.versions = versions;
        this.registry = new AgentSkillRegistry();
        this.catalog = null;
        this.commandParser = new AgentContextCommandParser();
        this.unsafeRequestPolicy = new AgentUnsafeRequestPolicy();
    }

    public Optional<DoctorAgentSkillResult> handle(Long sessionId, String question, CurrentUserVO user) {
        if (user == null || user.getRoleCode() != UserRole.DOCTOR) return Optional.empty();
        unsafeRequestPolicy.requireAllowed(question, user.getRoleCode());
        Optional<AgentQueryContextSnapshot> existing = contexts.load(sessionId);
        validateTypedReferenceCommand(question, existing.orElse(null));
        if (existing.isEmpty() && commandParser.isSelectionCommand(question)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "查询上下文已过期，请重新查询列表");
        }
        AgentSkillRoute route = existing.flatMap(context -> commandParser.parse(question, context))
                .orElseGet(() -> catalog == null
                        ? router.route(question, existing.map(AgentQueryContextSnapshot::currentSkill).orElse(null))
                        : router.route(question, existing.map(AgentQueryContextSnapshot::currentSkill).orElse(null),
                        catalog.availableFor(sessionId, user)));
        if (route.confidence() < MIN_ROUTE_CONFIDENCE) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR,
                    "请说明您要查询任务、临床待办还是医学知识");
        }
        Map<String, String> normalized = registry.validateAndNormalize(
                route.skillCode(), user.getRoleCode(), route.arguments());
        route = new AgentSkillRoute(route.skillCode(), route.confidence(), normalized,
                route.command(), route.selectedIndex());
        Optional<DoctorAgentSkillResult> result = switch (route.skillCode()) {
            case DOCTOR_WORKLOAD_OVERVIEW -> Optional.of(workload(user, route));
            case ASSIGNED_CASE_SEARCH -> Optional.of(search(sessionId, route, existing, user));
            case CASE_CLINICAL_SUMMARY -> Optional.of(summary(sessionId, question, route, existing, user));
            case CASE_FOLLOWUP_ANALYSIS -> Optional.of(followup(sessionId, question, route, existing, user));
            case DOCTOR_TASK_SEARCH -> Optional.of(tasks(sessionId, question, route, existing, user));
            case DOCTOR_CLINICAL_QUEUE -> Optional.of(clinicalQueue(sessionId, route, existing, user));
            case MEDICAL_KNOWLEDGE_QA -> Optional.empty();
            case MY_CASE_LIST, MY_CASE_PROGRESS, MY_SIGNED_REPORT, PATIENT_KNOWLEDGE_QA -> Optional.empty();
        };
        if (result.isEmpty()) return result;
        AgentSkillRuntimeVersion version = versions.resolve(sessionId, route.skillCode());
        DoctorAgentSkillResult value = result.orElseThrow();
        return Optional.of(new DoctorAgentSkillResult(value.skillCode(), version.version(), value.confidence(),
                value.answer(), value.data(), value.pagination(), value.actions()));
    }

    private DoctorAgentSkillResult tasks(Long sessionId, String question, AgentSkillRoute route,
                                         Optional<AgentQueryContextSnapshot> existing, CurrentUserVO user) {
        AgentQueryContextSnapshot previous = existing.orElse(null);
        if (route.command() == AgentContextCommand.SELECT_INDEX) {
            requireReferenceContext(previous, AgentReferenceType.TASK, "请重新查询任务列表");
            long taskId = selectedReference(previous, route.selectedIndex());
            return taskDetail(sessionId, route, String.valueOf(taskId), user,
                    AgentReferenceType.TASK, null, false);
        }
        String explicitReference = route.arguments().get("taskReference");
        if (explicitReference != null && !explicitReference.isBlank()) {
            return taskDetail(sessionId, route, explicitReference, user,
                    AgentReferenceType.TASK, null, false);
        }

        int page = 1;
        AnalysisTaskType taskType = taskTypeFromQuestion(question);
        DoctorTaskStatusFilter status = enumValue(DoctorTaskStatusFilter.class,
                route.arguments().get("status"), DoctorTaskStatusFilter.ANY);
        DoctorDateWindow dateWindow = enumValue(DoctorDateWindow.class,
                route.arguments().get("dateWindow"), DoctorDateWindow.ANY);
        if (route.command() == AgentContextCommand.NEXT_PAGE
                || route.command() == AgentContextCommand.PREVIOUS_PAGE
                || route.command() == AgentContextCommand.FILTER_FAILED) {
            requireReferenceContext(previous, AgentReferenceType.TASK, "请重新查询任务列表");
            taskType = previous.taskType();
            status = route.command() == AgentContextCommand.FILTER_FAILED
                    ? DoctorTaskStatusFilter.FAILED : previous.taskStatus();
            dateWindow = previous.dateWindow();
            page = route.command() == AgentContextCommand.NEXT_PAGE ? previous.page() + 1
                    : route.command() == AgentContextCommand.PREVIOUS_PAGE ? Math.max(1, previous.page() - 1) : 1;
        }
        DoctorTaskSearchCriteria criteria = new DoctorTaskSearchCriteria(taskType, status, dateWindow);
        var pageResult = queries.searchTasks(criteria, page, 10, user);
        rejectPastLastPage(pageResult.getTotal(), pageResult.getRecords(), page);
        List<Long> ids = pageResult.getRecords().stream().map(task -> task.getTaskId()).toList();
        contexts.save(sessionId, new AgentQueryContextSnapshot(
                AgentSkillCode.DOCTOR_TASK_SEARCH, AgentReferenceType.TASK,
                SegmentationState.ANY, DoctorClinicalState.ANY, taskType, status, null, dateWindow, null,
                pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal(), null, null, ids));
        AgentPagination pagination = pagination(pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal());
        List<AgentAction> actions = paginationActions(pagination);
        pageResult.getRecords().forEach(task -> actions.add(new AgentAction(
                "VIEW_TASK", "查看任务", task.getTaskId(), "/tasks/" + task.getTaskId())));
        String answer = pageResult.getTotal() == 0 ? "没有找到符合当前条件的任务。"
                : "共找到 " + pageResult.getTotal() + " 个任务，以下是第 " + pageResult.getPageNo() + " 页。";
        return result(route, answer, "TASK_LIST", Map.of("tasks", pageResult.getRecords()), pagination, actions);
    }

    private DoctorAgentSkillResult clinicalQueue(Long sessionId, AgentSkillRoute route,
                                                  Optional<AgentQueryContextSnapshot> existing,
                                                  CurrentUserVO user) {
        AgentQueryContextSnapshot previous = existing.orElse(null);
        if (route.command() == AgentContextCommand.SELECT_INDEX) {
            requireReferenceContext(previous, AgentReferenceType.CLINICAL_QUEUE, "请重新查询临床队列");
            long taskId = selectedReference(previous, route.selectedIndex());
            return taskDetail(sessionId, route, String.valueOf(taskId), user,
                    AgentReferenceType.CLINICAL_QUEUE, previous.queueType(), true);
        }
        int page = 1;
        DoctorClinicalQueueType queueType = enumValue(DoctorClinicalQueueType.class,
                route.arguments().get("queueType"), null);
        DoctorDateWindow dateWindow = enumValue(DoctorDateWindow.class,
                route.arguments().get("dateWindow"), DoctorDateWindow.ANY);
        if (route.command() == AgentContextCommand.NEXT_PAGE || route.command() == AgentContextCommand.PREVIOUS_PAGE) {
            requireReferenceContext(previous, AgentReferenceType.CLINICAL_QUEUE, "请重新查询临床队列");
            queueType = previous.queueType();
            dateWindow = previous.dateWindow();
            page = route.command() == AgentContextCommand.NEXT_PAGE ? previous.page() + 1
                    : Math.max(1, previous.page() - 1);
        }
        if (queueType == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请说明要查询待审核结果还是待签发报告");
        }
        DoctorClinicalQueueCriteria criteria = new DoctorClinicalQueueCriteria(queueType, dateWindow);
        var pageResult = queries.searchClinicalQueue(criteria, page, 10, user);
        rejectPastLastPage(pageResult.getTotal(), pageResult.getRecords(), page);
        List<Long> ids = pageResult.getRecords().stream().map(item -> item.getTaskId()).toList();
        contexts.save(sessionId, new AgentQueryContextSnapshot(
                AgentSkillCode.DOCTOR_CLINICAL_QUEUE, AgentReferenceType.CLINICAL_QUEUE,
                SegmentationState.ANY, DoctorClinicalState.ANY, AnalysisTaskType.VESSEL_SEGMENTATION,
                DoctorTaskStatusFilter.ANY, queueType, dateWindow, null,
                pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal(), null, null, ids));
        AgentPagination pagination = pagination(pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal());
        List<AgentAction> actions = paginationActions(pagination);
        pageResult.getRecords().forEach(item -> actions.add(reviewAction(item.getTaskId())));
        String label = queueType == DoctorClinicalQueueType.PENDING_REVIEW ? "待审核结果" : "待签发报告";
        String answer = pageResult.getTotal() == 0 ? "当前没有" + label + "。"
                : "共找到 " + pageResult.getTotal() + " 条" + label + "，以下是第 "
                + pageResult.getPageNo() + " 页。";
        return result(route, answer, "CLINICAL_QUEUE",
                Map.of("queueType", queueType.name(), "items", pageResult.getRecords()), pagination, actions);
    }

    private DoctorAgentSkillResult taskDetail(Long sessionId, AgentSkillRoute route, String reference,
                                               CurrentUserVO user, AgentReferenceType referenceType,
                                               DoctorClinicalQueueType queueType,
                                               boolean openReview) {
        var detail = queries.getTaskDetail(reference, user);
        contexts.save(sessionId, new AgentQueryContextSnapshot(
                route.skillCode(), referenceType, SegmentationState.ANY, DoctorClinicalState.ANY,
                detail.getTaskType(), DoctorTaskStatusFilter.ANY,
                queueType,
                DoctorDateWindow.ANY, null, 1, 10, 1, detail.getCaseId(), detail.getTaskId(),
                List.of(detail.getTaskId())));
        AgentAction action = openReview ? reviewAction(detail.getTaskId())
                : new AgentAction("VIEW_TASK", "查看任务", detail.getTaskId(), "/tasks/" + detail.getTaskId());
        String taskLabel = detail.getTaskNo() == null ? String.valueOf(detail.getTaskId()) : detail.getTaskNo();
        return result(route, "已找到任务 " + taskLabel + "。", "TASK_DETAIL",
                Map.of("taskDetail", detail), null, List.of(action));
    }

    private AgentAction reviewAction(Long taskId) {
        return new AgentAction("VIEW_REVIEW", "进入审核", taskId,
                "/tasks/" + taskId + "?tab=clinical&stage=review");
    }

    private List<AgentAction> paginationActions(AgentPagination pagination) {
        List<AgentAction> actions = new ArrayList<>();
        if (pagination.hasPrevious()) actions.add(new AgentAction("PREVIOUS_PAGE", "上一页", null, null));
        if (pagination.hasNext()) actions.add(new AgentAction("NEXT_PAGE", "下一页", null, null));
        return actions;
    }

    private long selectedReference(AgentQueryContextSnapshot context, Integer selectedIndex) {
        int index = selectedIndex == null ? -1 : selectedIndex;
        if (index < 1 || index > context.recentReferenceIds().size()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "所选序号不在当前页范围内");
        }
        return context.recentReferenceIds().get(index - 1);
    }

    private void requireReferenceContext(AgentQueryContextSnapshot context, AgentReferenceType expected,
                                         String message) {
        if (context == null || context.referenceType() != expected) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, message);
        }
    }

    private void rejectPastLastPage(long total, List<?> records, int page) {
        if (total > 0 && records.isEmpty() && page > 1) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "已经是最后一页");
        }
    }

    private void validateTypedReferenceCommand(String question, AgentQueryContextSnapshot context) {
        if (context == null || !commandParser.isSelectionCommand(question)) return;
        if (question.contains("病例") && context.referenceType() != AgentReferenceType.CASE) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "查询上下文不匹配，请重新查询病例列表");
        }
        if (question.contains("任务") && context.referenceType() != AgentReferenceType.TASK) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "查询上下文不匹配，请重新查询任务列表");
        }
        if ((question.contains("待审核") || question.contains("待签发"))
                && context.referenceType() != AgentReferenceType.CLINICAL_QUEUE) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "查询上下文不匹配，请重新查询临床队列");
        }
    }

    private AnalysisTaskType taskTypeFromQuestion(String question) {
        String value = question == null ? "" : question;
        if (value.contains("图像质检") || value.contains("图像质量")
                || value.contains("质量检测") || value.contains("质检任务")) {
            return AnalysisTaskType.IMAGE_QUALITY_CHECK;
        }
        return AnalysisTaskType.VESSEL_SEGMENTATION;
    }

    private DoctorAgentSkillResult workload(CurrentUserVO user, AgentSkillRoute route) {
        var value = queries.getClinicalWorkload(user);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patientCount", value.patientCount());
        payload.put("caseCount", value.caseCount());
        payload.put("unsegmentedCaseCount", value.unsegmentedCaseCount());
        payload.put("incompleteSegmentationCaseCount", value.incompleteSegmentationCaseCount());
        payload.put("pendingReviewCount", value.pendingReviewCount());
        payload.put("pendingReportCount", value.pendingReportCount());
        String answer = "你目前负责 " + value.patientCount() + " 名患者、" + value.caseCount()
                + " 个病例，其中 " + value.unsegmentedCaseCount() + " 个病例尚未创建血管分割任务，"
                + value.pendingReviewCount() + " 个结果待审核。";
        return result(route, answer, "METRICS", payload, null, List.of());
    }

    private DoctorAgentSkillResult search(Long sessionId, AgentSkillRoute route,
                                          Optional<AgentQueryContextSnapshot> existing, CurrentUserVO user) {
        AgentQueryContextSnapshot previous = existing.orElse(null);
        int page = 1;
        SegmentationState state = state(route.arguments().get("segmentationState"));
        DoctorClinicalState clinicalState = enumValue(DoctorClinicalState.class,
                route.arguments().get("clinicalState"), DoctorClinicalState.ANY);
        DoctorDateWindow dateWindow = enumValue(DoctorDateWindow.class,
                route.arguments().get("dateWindow"), DoctorDateWindow.ANY);
        EyeSide eyeSide = enumValue(EyeSide.class, route.arguments().get("eyeSide"), null);
        if (route.command() == AgentContextCommand.NEXT_PAGE || route.command() == AgentContextCommand.PREVIOUS_PAGE
                || route.command() == AgentContextCommand.FILTER_FAILED) {
            if (previous == null || previous.currentSkill() != AgentSkillCode.ASSIGNED_CASE_SEARCH) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "查询上下文已过期，请重新查询病例列表");
            }
            state = route.command() == AgentContextCommand.FILTER_FAILED
                    ? SegmentationState.FAILED : previous.segmentationState();
            clinicalState = previous.clinicalState();
            dateWindow = previous.dateWindow();
            eyeSide = previous.eyeSide();
            page = route.command() == AgentContextCommand.NEXT_PAGE ? previous.page() + 1
                    : route.command() == AgentContextCommand.PREVIOUS_PAGE ? Math.max(1, previous.page() - 1) : 1;
        }
        DoctorCaseSearchCriteria criteria = new DoctorCaseSearchCriteria(
                state, null, clinicalState, dateWindow, eyeSide);
        var pageResult = queries.searchAssignedCases(criteria, page, 10, user);
        if (pageResult.getTotal() > 0 && pageResult.getRecords().isEmpty() && page > 1) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "已经是最后一页");
        }
        List<Integer> ids = pageResult.getRecords().stream().map(DoctorAgentCaseSummaryVO::getCaseId).toList();
        contexts.save(sessionId, new AgentQueryContextSnapshot(AgentSkillCode.ASSIGNED_CASE_SEARCH, state,
                clinicalState, dateWindow, eyeSide,
                pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal(), null, null, ids));
        Map<String, Object> payload = Map.of("cases", pageResult.getRecords());
        AgentPagination pagination = pagination(pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal());
        List<AgentAction> actions = new ArrayList<>();
        if (pagination.hasPrevious()) actions.add(new AgentAction("PREVIOUS_PAGE", "上一页", null, null));
        if (pagination.hasNext()) actions.add(new AgentAction("NEXT_PAGE", "下一页", null, null));
        String answer = pageResult.getTotal() == 0 ? "没有找到符合当前条件的病例。"
                : "共找到 " + pageResult.getTotal() + " 个病例，以下是按最近更新时间排列的第 "
                + pageResult.getPageNo() + " 页。";
        return result(route, answer, "CASE_LIST", payload, pagination, actions);
    }

    private DoctorAgentSkillResult summary(Long sessionId, String question, AgentSkillRoute route,
                                           Optional<AgentQueryContextSnapshot> existing, CurrentUserVO user) {
        String reference = route.arguments().get("caseReference");
        if (reference == null || reference.isBlank()) reference = extractReference(question);
        if (route.command() == AgentContextCommand.SELECT_INDEX) {
            AgentQueryContextSnapshot context = existing.orElseThrow(() ->
                    new BaseException(ErrorMessageSignal.PARAM_ERROR, "查询上下文已过期，请重新查询病例列表"));
            int index = route.selectedIndex() == null ? -1 : route.selectedIndex();
            if (index < 1 || index > context.recentReferenceIds().size()) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "所选序号不在当前页范围内");
            }
            reference = String.valueOf(context.recentReferenceIds().get(index - 1));
        }
        var item = references.resolveCase(reference, user);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("caseId", item.getId()); detail.put("caseNo", item.getCaseNo());
        detail.put("patientNo", item.getPatientNo()); detail.put("patientAge", item.getPatientAge());
        detail.put("patientGender", item.getPatientGender()); detail.put("eyeSide", item.getEyeSide());
        detail.put("workflowStatus", item.getWorkflowStatus()); detail.put("updatedAt", item.getUpdatedAt());
        contexts.save(sessionId, new AgentQueryContextSnapshot(AgentSkillCode.CASE_CLINICAL_SUMMARY,
                SegmentationState.ANY, 1, 10, 1, item.getId(), null, List.of(item.getId())));
        List<AgentAction> actions = List.of(new AgentAction("VIEW_CASE", "查看病例", item.getId().longValue(),
                "/cases/" + item.getId() + "/images"));
        return result(route, "已找到病例 " + item.getCaseNo() + "，当前流程状态为 " + item.getWorkflowStatus() + "。",
                "CASE_DETAIL", Map.of("caseDetail", detail), null, actions);
    }

    private DoctorAgentSkillResult followup(Long sessionId, String question, AgentSkillRoute route,
                                            Optional<AgentQueryContextSnapshot> existing, CurrentUserVO user) {
        String reference = route.arguments().get("caseReference");
        if (reference == null || reference.isBlank()) reference = extractReference(question);
        if ((reference == null || reference.isBlank()) && existing.isPresent()
                && existing.get().selectedCaseId() != null) reference = String.valueOf(existing.get().selectedCaseId());
        var item = references.resolveCase(reference, user);
        CaseAnalysisTimelineVO timeline = timelines.timeline(item.getId().longValue(), null,
                TaskType.VESSEL_SEGMENTATION, null, null);
        var resultIds = timeline.items().stream().map(CaseAnalysisTimelineVO.Item::resultId)
                .filter(java.util.Objects::nonNull).toList();
        if (resultIds.size() < 2) throw new BaseException(ErrorMessageSignal.PARAM_ERROR,
                "当前病例没有足够的同类历史结果可供比较");
        var comparison = timelines.compare(resultIds.get(resultIds.size() - 2), resultIds.get(resultIds.size() - 1));
        contexts.save(sessionId, new AgentQueryContextSnapshot(AgentSkillCode.CASE_FOLLOWUP_ANALYSIS,
                SegmentationState.ANY, 1, 10, 1, item.getId(), null, List.of(item.getId())));
        return new DoctorAgentSkillResult(AgentSkillCode.CASE_FOLLOWUP_ANALYSIS, 0.98,
                "已比较病例 " + item.getCaseNo() + " 最近两次血管分割结果。变化仅供医生复核参考。",
                new AgentStructuredData("COMPARISON", Map.of("comparison", comparison)), null, List.of());
    }

    private DoctorAgentSkillResult result(AgentSkillRoute route, String answer, String type,
                                          Map<String, Object> payload, AgentPagination pagination,
                                          List<AgentAction> actions) {
        return new DoctorAgentSkillResult(route.skillCode(), route.confidence(), answer,
                new AgentStructuredData(type, payload), pagination, actions);
    }

    private AgentPagination pagination(int page, int size, long total) {
        return new AgentPagination(page, size, total, page > 1, (long) page * size < total);
    }

    private SegmentationState state(String value) {
        if (value == null || value.isBlank()) return SegmentationState.ANY;
        try { return SegmentationState.valueOf(value); }
        catch (IllegalArgumentException ignored) { return SegmentationState.ANY; }
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private String extractReference(String question) {
        if (question == null) return null;
        for (String token : question.replace('，', ' ').replace('。', ' ').split("\\s+")) {
            String cleaned = token.replaceAll("^(查看|病例号|病例|患者|匿名患者编号)", "")
                    .replaceAll("[？?！!]$", "");
            if (cleaned.matches("(?i)(C|PT-|TEST|P0).*|\\d+")) return cleaned;
        }
        return null;
    }
}
