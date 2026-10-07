package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.PatientAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.PatientAgentProgressVO;
import com.example.retinavision.pojo.VO.PatientAgentReportDetailVO;
import com.example.retinavision.pojo.VO.PatientAgentSignedReportVO;
import com.example.retinavision.service.PatientAgentQueryService;
import com.example.retinavision.service.PatientReportExplanationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PatientAgentSkillOrchestrator {
    private static final double MIN_ROUTE_CONFIDENCE = 0.65;
    private final AgentSkillRouter router;
    private final PatientAgentQueryService queries;
    private final PatientAgentQueryContextService contexts;
    private final PatientReportExplanationService explanations;
    private final AgentSkillVersionResolver versions;
    private final AgentSkillRegistry registry;
    private final AgentSkillCatalogService catalog;
    private final PatientAgentContextCommandParser commandParser;

    @Autowired
    public PatientAgentSkillOrchestrator(AgentSkillRouter router,
                                         PatientAgentQueryService queries,
                                         PatientAgentQueryContextService contexts,
                                         PatientReportExplanationService explanations,
                                          AgentSkillVersionResolver versions,
                                         AgentSkillRegistry registry,
                                         AgentSkillCatalogService catalog,
                                         PatientAgentContextCommandParser commandParser) {
        this.router = router;
        this.queries = queries;
        this.contexts = contexts;
        this.explanations = explanations;
        this.versions = versions;
        this.registry = registry;
        this.catalog = catalog;
        this.commandParser = commandParser;
    }

    public PatientAgentSkillOrchestrator(AgentSkillRouter router,
                                         PatientAgentQueryService queries,
                                         PatientAgentQueryContextService contexts,
                                         PatientReportExplanationService explanations,
                                          AgentSkillVersionResolver versions) {
        this(router, queries, contexts, explanations, versions, new AgentSkillRegistry(), null,
                new PatientAgentContextCommandParser());
    }

    public Optional<PatientAgentSkillResult> handle(Long sessionId, String question, CurrentUserVO user) {
        if (user == null || user.getRoleCode() != UserRole.USER) return Optional.empty();
        Optional<PatientAgentQueryContextSnapshot> existing = contexts.load(sessionId);
        if (existing.isEmpty() && commandParser.isContextCommand(question)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "查询上下文已过期，请重新查询检查列表");
        }
        AgentSkillRoute route = existing.flatMap(context -> commandParser.parse(question, context))
                .orElseGet(() -> catalog == null
                        ? router.route(question, existing.map(PatientAgentQueryContextSnapshot::currentSkill).orElse(null))
                        : router.route(question,
                        existing.map(PatientAgentQueryContextSnapshot::currentSkill).orElse(null),
                        catalog.availableFor(sessionId, user)));
        if (route.confidence() < MIN_ROUTE_CONFIDENCE) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR,
                    "请说明您要查询检查进度、正式报告还是医学知识");
        }
        Map<String, String> arguments = registry.validateAndNormalize(
                route.skillCode(), user.getRoleCode(), route.arguments());
        route = new AgentSkillRoute(route.skillCode(), route.confidence(), arguments,
                route.command(), route.selectedIndex());
        if (route.skillCode() == AgentSkillCode.PATIENT_KNOWLEDGE_QA) return Optional.empty();

        PatientAgentSkillResult result = switch (route.skillCode()) {
            case MY_CASE_LIST -> listCases(sessionId, route, existing, user);
            case MY_CASE_PROGRESS -> progress(sessionId, route, existing, user);
            case MY_SIGNED_REPORT -> reports(sessionId, route, existing, user);
            case PATIENT_KNOWLEDGE_QA, DOCTOR_WORKLOAD_OVERVIEW, ASSIGNED_CASE_SEARCH,
                    CASE_CLINICAL_SUMMARY, CASE_FOLLOWUP_ANALYSIS, DOCTOR_TASK_SEARCH,
                    DOCTOR_CLINICAL_QUEUE, MEDICAL_KNOWLEDGE_QA -> throw new BaseException(
                    ErrorMessageSignal.FORBIDDEN, "无权使用该 Skill");
        };
        AgentSkillRuntimeVersion version = versions.resolve(sessionId, route.skillCode());
        return Optional.of(new PatientAgentSkillResult(result.skillCode(), version.version(),
                result.confidence(), result.answer(), result.data(), result.pagination(), result.actions()));
    }

    private PatientAgentSkillResult listCases(Long sessionId, AgentSkillRoute route,
                                               Optional<PatientAgentQueryContextSnapshot> existing,
                                               CurrentUserVO user) {
        int page = 1;
        boolean reuploadOnly = "TRUE".equals(route.arguments().get("reuploadOnly"));
        boolean signedOnly = "TRUE".equals(route.arguments().get("signedReportOnly"));
        if (route.command() == AgentContextCommand.NEXT_PAGE
                || route.command() == AgentContextCommand.PREVIOUS_PAGE) {
            PatientAgentQueryContextSnapshot previous = requireContext(existing, AgentSkillCode.MY_CASE_LIST,
                    "请重新查询检查列表");
            page = route.command() == AgentContextCommand.NEXT_PAGE ? previous.page() + 1
                    : Math.max(1, previous.page() - 1);
            reuploadOnly = previous.reuploadOnly();
            signedOnly = previous.signedReportOnly();
        }
        PatientCaseSearchCriteria criteria = new PatientCaseSearchCriteria(reuploadOnly, signedOnly);
        var pageResult = queries.listMyCases(criteria, page, 10, user);
        rejectPastLastPage(pageResult.getTotal(), pageResult.getRecords(), page);
        List<PatientAgentReference> references = pageResult.getRecords().stream()
                .map(item -> new PatientAgentReference(item.getCaseId(), item.getCaseNo(), null, null)).toList();
        contexts.save(sessionId, new PatientAgentQueryContextSnapshot(
                AgentSkillCode.MY_CASE_LIST, reuploadOnly, signedOnly,
                pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal(),
                null, null, null, references));
        AgentPagination pagination = pagination(pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal());
        List<AgentAction> actions = paginationActions(pagination);
        for (PatientAgentCaseSummaryVO item : pageResult.getRecords()) {
            actions.add(action("VIEW_CASE_PROGRESS", "查看进度", item.getCaseId(),
                    "/cases/" + item.getCaseId() + "/progress"));
            if (item.getQualityStatus() == PatientQualityDisplayStatus.REUPLOAD_RECOMMENDED) {
                actions.add(action("GO_TO_IMAGE_UPLOAD", "重新上传图像", item.getCaseId(),
                        "/cases/" + item.getCaseId() + "/images"));
            }
        }
        String answer = pageResult.getTotal() == 0 ? "当前没有符合条件的检查。"
                : "共找到 " + pageResult.getTotal() + " 个检查，以下是按最近更新时间排列的第 "
                + pageResult.getPageNo() + " 页。";
        return result(route, answer, "CASE_LIST", Map.of("cases", pageResult.getRecords()), pagination, actions);
    }

    private PatientAgentSkillResult progress(Long sessionId, AgentSkillRoute route,
                                              Optional<PatientAgentQueryContextSnapshot> existing,
                                              CurrentUserVO user) {
        String reference = route.arguments().get("caseReference");
        if (route.command() == AgentContextCommand.SELECT_INDEX) {
            reference = String.valueOf(selected(existing, route.selectedIndex()).caseId());
        }
        if ((reference == null || reference.isBlank()) && existing.isPresent()
                && existing.get().selectedCaseId() != null) {
            reference = String.valueOf(existing.get().selectedCaseId());
        }
        if (reference == null || reference.isBlank()) {
            var latest = queries.listMyCases(new PatientCaseSearchCriteria(false, false), 1, 1, user);
            if (latest.getRecords().isEmpty()) {
                return result(route, "当前还没有检查记录。", "CASE_PROGRESS",
                        Map.of("empty", true), null, List.of());
            }
            reference = String.valueOf(latest.getRecords().get(0).getCaseId());
        }
        PatientAgentProgressVO value = queries.getMyCaseProgress(reference, user);
        PatientAgentReference selected = new PatientAgentReference(
                value.getCaseId(), value.getCaseNo(), null, null);
        contexts.save(sessionId, new PatientAgentQueryContextSnapshot(
                AgentSkillCode.MY_CASE_PROGRESS, false, false, 1, 10, 1,
                value.getCaseId(), null, null, List.of(selected)));
        List<AgentAction> actions = new ArrayList<>();
        actions.add(action("VIEW_CASE_PROGRESS", "查看进度", value.getCaseId(),
                "/cases/" + value.getCaseId() + "/progress"));
        if (value.getQualityStatus() == PatientQualityDisplayStatus.REUPLOAD_RECOMMENDED) {
            actions.add(action("GO_TO_IMAGE_UPLOAD", "重新上传图像", value.getCaseId(),
                    "/cases/" + value.getCaseId() + "/images"));
        }
        String answer = "检查 " + value.getCaseNo() + " 当前处于“"
                + stageLabel(value.getCurrentStage()) + "”阶段，下一处理方为" + value.getNextHandler() + "。";
        return result(route, answer, "CASE_PROGRESS", Map.of("progress", value), null, actions);
    }

    private PatientAgentSkillResult reports(Long sessionId, AgentSkillRoute route,
                                             Optional<PatientAgentQueryContextSnapshot> existing,
                                             CurrentUserVO user) {
        String mode = route.arguments().getOrDefault("mode", "LIST");
        if (route.command() == AgentContextCommand.SELECT_INDEX || "VIEW".equals(mode)
                || "EXPLAIN".equals(mode)) {
            return reportDetail(sessionId, route, existing, user, mode);
        }
        int page = 1;
        if (route.command() == AgentContextCommand.NEXT_PAGE
                || route.command() == AgentContextCommand.PREVIOUS_PAGE) {
            PatientAgentQueryContextSnapshot previous = requireContext(existing,
                    AgentSkillCode.MY_SIGNED_REPORT, "请重新查询正式报告列表");
            page = route.command() == AgentContextCommand.NEXT_PAGE ? previous.page() + 1
                    : Math.max(1, previous.page() - 1);
        }
        String reference = route.arguments().getOrDefault("caseReference", "");
        var pageResult = queries.listMySignedReports(reference, page, 10, user);
        rejectPastLastPage(pageResult.getTotal(), pageResult.getRecords(), page);
        List<PatientAgentReference> references = pageResult.getRecords().stream().map(item ->
                new PatientAgentReference(item.getCaseId(), item.getCaseNo(),
                        item.getResultId(), item.getVersion())).toList();
        contexts.save(sessionId, new PatientAgentQueryContextSnapshot(
                AgentSkillCode.MY_SIGNED_REPORT, false, true,
                pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal(),
                null, null, null, references));
        AgentPagination pagination = pagination(pageResult.getPageNo(), pageResult.getPageSize(), pageResult.getTotal());
        List<AgentAction> actions = paginationActions(pagination);
        for (PatientAgentSignedReportVO item : pageResult.getRecords()) {
            actions.add(reportAction(item.getCaseId()));
        }
        String answer = pageResult.getTotal() == 0 ? "当前没有已签发的正式报告。"
                : "共找到 " + pageResult.getTotal() + " 份正式报告，以下是第 "
                + pageResult.getPageNo() + " 页。";
        return result(route, answer, "SIGNED_REPORT", Map.of("reports", pageResult.getRecords()),
                pagination, actions);
    }

    private PatientAgentSkillResult reportDetail(Long sessionId, AgentSkillRoute route,
                                                  Optional<PatientAgentQueryContextSnapshot> existing,
                                                  CurrentUserVO user, String mode) {
        PatientAgentReference reference;
        if (route.command() == AgentContextCommand.SELECT_INDEX) {
            reference = selected(existing, route.selectedIndex());
        } else if (!route.arguments().getOrDefault("caseReference", "").isBlank()
                || existing.isEmpty()) {
            String caseReference = route.arguments().getOrDefault("caseReference", "");
            var reports = queries.listMySignedReports(caseReference, 1, 1, user);
            if (reports.getRecords().isEmpty()) {
                throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
            }
            PatientAgentSignedReportVO report = reports.getRecords().get(0);
            reference = new PatientAgentReference(report.getCaseId(), report.getCaseNo(),
                    report.getResultId(), report.getVersion());
        } else {
            PatientAgentQueryContextSnapshot context = requireContext(existing,
                    AgentSkillCode.MY_SIGNED_REPORT, "请重新查询正式报告列表");
            if (context.selectedResultId() != null) {
                reference = new PatientAgentReference(context.selectedCaseId(), null,
                        context.selectedResultId(), context.selectedReportVersion());
            } else if (context.references().size() == 1) {
                reference = context.references().get(0);
            } else {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请先选择要查看的正式报告");
            }
        }
        if (reference.resultId() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "报告引用已失效，请重新查询正式报告列表");
        }
        PatientAgentReportDetailVO detail = queries.getMySignedReport(
                String.valueOf(reference.caseId()), reference.resultId(), reference.reportVersion(), user);
        contexts.save(sessionId, new PatientAgentQueryContextSnapshot(
                AgentSkillCode.MY_SIGNED_REPORT, false, true, 1, 10, 1,
                detail.getCaseId(), detail.getResultId(), detail.getVersion(),
                List.of(new PatientAgentReference(detail.getCaseId(), detail.getCaseNo(),
                        detail.getResultId(), detail.getVersion()))));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("report", detail);
        String answer = "已找到检查 " + detail.getCaseNo() + " 的正式报告。";
        if ("EXPLAIN".equals(mode)) {
            PatientReportExplanationResult explanation = explanations.explain(detail);
            payload.put("explanationAvailable", explanation.available());
            if (explanation.explanation() != null) payload.put("explanation", explanation.explanation());
            if (explanation.message() != null) payload.put("explanationMessage", explanation.message());
            answer = explanation.available() ? "已为您生成这份正式报告的通俗解释。" : explanation.message();
        }
        return result(route, answer, "SIGNED_REPORT", payload, null,
                List.of(reportAction(detail.getCaseId())));
    }

    private PatientAgentReference selected(Optional<PatientAgentQueryContextSnapshot> context,
                                           Integer selectedIndex) {
        PatientAgentQueryContextSnapshot value = context.orElseThrow(() ->
                new BaseException(ErrorMessageSignal.PARAM_ERROR, "查询上下文已过期，请重新查询列表"));
        int index = selectedIndex == null ? -1 : selectedIndex;
        if (index < 1 || index > value.references().size()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "所选序号不在当前页范围内");
        }
        return value.references().get(index - 1);
    }

    private PatientAgentQueryContextSnapshot requireContext(
            Optional<PatientAgentQueryContextSnapshot> context, AgentSkillCode expected, String message) {
        PatientAgentQueryContextSnapshot value = context.orElseThrow(() ->
                new BaseException(ErrorMessageSignal.PARAM_ERROR, message));
        if (value.currentSkill() != expected) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, message);
        }
        return value;
    }

    private void rejectPastLastPage(long total, List<?> records, int page) {
        if (total > 0 && records.isEmpty() && page > 1) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "已经是最后一页");
        }
    }

    private List<AgentAction> paginationActions(AgentPagination pagination) {
        List<AgentAction> actions = new ArrayList<>();
        if (pagination.hasPrevious()) actions.add(new AgentAction("PREVIOUS_PAGE", "上一页", null, null));
        if (pagination.hasNext()) actions.add(new AgentAction("NEXT_PAGE", "下一页", null, null));
        return actions;
    }

    private AgentAction reportAction(Long caseId) {
        return action("VIEW_SIGNED_REPORT", "查看正式报告", caseId,
                "/cases/" + caseId + "/progress?section=reports");
    }

    private AgentAction action(String type, String label, Long id, String path) {
        if (!path.equals("/cases/" + id + "/progress")
                && !path.equals("/cases/" + id + "/progress?section=reports")
                && !path.equals("/cases/" + id + "/images")) {
            throw new IllegalArgumentException("不支持的患者页面动作");
        }
        return new AgentAction(type, label, id, path);
    }

    private AgentPagination pagination(int page, int size, long total) {
        return new AgentPagination(page, size, total, page > 1, (long) page * size < total);
    }

    private PatientAgentSkillResult result(AgentSkillRoute route, String answer, String type,
                                           Map<String, Object> payload, AgentPagination pagination,
                                           List<AgentAction> actions) {
        return new PatientAgentSkillResult(route.skillCode(), route.confidence(), answer,
                new AgentStructuredData(type, payload), pagination, List.copyOf(actions));
    }

    private String stageLabel(String code) {
        if (code == null) return "状态更新";
        return switch (code) {
            case "IMAGE_UPLOAD" -> "上传图像";
            case "QUALITY_CHECK" -> "图像质量检查";
            case "DOCTOR_PROCESSING" -> "医生处理";
            case "FORMAL_REPORT" -> "正式报告";
            default -> "状态更新";
        };
    }
}
