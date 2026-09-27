package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.VO.CaseAnalysisTimelineVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import com.example.retinavision.service.AgentClinicalReferenceService;
import com.example.retinavision.service.CaseAnalysisTimelineService;
import com.example.retinavision.service.DoctorAgentQueryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DoctorAgentSkillOrchestrator {
    private final AgentSkillRouter router;
    private final DoctorAgentQueryService queries;
    private final AgentQueryContextService contexts;
    private final AgentClinicalReferenceService references;
    private final CaseAnalysisTimelineService timelines;
    private final AgentSkillVersionBindingService versions;

    public DoctorAgentSkillOrchestrator(AgentSkillRouter router, DoctorAgentQueryService queries,
                                        AgentQueryContextService contexts, AgentClinicalReferenceService references,
                                        CaseAnalysisTimelineService timelines,
                                        AgentSkillVersionBindingService versions) {
        this.router = router;
        this.queries = queries;
        this.contexts = contexts;
        this.references = references;
        this.timelines = timelines;
        this.versions = versions;
    }

    public Optional<DoctorAgentSkillResult> handle(Long sessionId, String question, CurrentUserVO user) {
        if (user == null || user.getRoleCode() != UserRole.DOCTOR) return Optional.empty();
        Optional<AgentQueryContextSnapshot> existing = contexts.load(sessionId);
        AgentSkillRoute route = router.route(question, existing.map(AgentQueryContextSnapshot::currentSkill).orElse(null));
        Optional<DoctorAgentSkillResult> result = switch (route.skillCode()) {
            case DOCTOR_WORKLOAD_OVERVIEW -> Optional.of(workload(user, route));
            case ASSIGNED_CASE_SEARCH -> Optional.of(search(sessionId, route, existing, user));
            case CASE_CLINICAL_SUMMARY -> Optional.of(summary(sessionId, question, route, existing, user));
            case CASE_FOLLOWUP_ANALYSIS -> Optional.of(followup(sessionId, question, existing, user));
            case DOCTOR_TASK_SEARCH, DOCTOR_CLINICAL_QUEUE, MEDICAL_KNOWLEDGE_QA -> Optional.empty();
        };
        if (result.isEmpty()) return result;
        AgentSkillRuntimeVersion version = versions.resolve(sessionId, route.skillCode());
        DoctorAgentSkillResult value = result.orElseThrow();
        return Optional.of(new DoctorAgentSkillResult(value.skillCode(), version.version(), value.confidence(),
                value.answer(), value.data(), value.pagination(), value.actions()));
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
        String reference = extractReference(question);
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

    private DoctorAgentSkillResult followup(Long sessionId, String question,
                                            Optional<AgentQueryContextSnapshot> existing, CurrentUserVO user) {
        String reference = extractReference(question);
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
