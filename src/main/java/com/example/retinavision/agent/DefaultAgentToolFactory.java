package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AgentToolCallLogMapper;
import com.example.retinavision.pojo.Entity.AgentToolCallLogEntity;
import com.example.retinavision.pojo.VO.AgentCitationVO;
import com.example.retinavision.pojo.VO.AgentToolCallSummaryVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.CaseAnalysisTimelineVO;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.rag.KnowledgeDocumentRetriever;
import com.example.retinavision.rag.KnowledgeAudiencePolicy;
import com.example.retinavision.service.CaseAnalysisTimelineService;
import com.example.retinavision.service.AgentClinicalReferenceService;
import com.example.retinavision.service.ClinicalAccessService;
import com.example.retinavision.service.QualityControlService;
import com.example.retinavision.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class DefaultAgentToolFactory implements AgentToolFactory {
    private static final int MAX_TOOL_OUTPUT = 8000;
    private static final int MAX_AUDIT_SUMMARY = 1000;
    private final KnowledgeDocumentRetriever retriever;
    private final KnowledgeAudiencePolicy audiencePolicy;
    private final ClinicalAccessService access;
    private final AgentClinicalReferenceService references;
    private final TaskService taskService;
    private final CaseAnalysisTimelineService timelineService;
    private final QualityControlService qualityControlService;
    private final AgentToolCallLogMapper logs;
    private final ObjectMapper json;

    public DefaultAgentToolFactory(KnowledgeDocumentRetriever retriever, KnowledgeAudiencePolicy audiencePolicy,
                                   ClinicalAccessService access,
                                   AgentClinicalReferenceService references,
                                   TaskService taskService, CaseAnalysisTimelineService timelineService,
                                   QualityControlService qualityControlService, AgentToolCallLogMapper logs,
                                   ObjectMapper json) {
        this.retriever = retriever;
        this.audiencePolicy = audiencePolicy;
        this.access = access;
        this.references = references;
        this.taskService = taskService;
        this.timelineService = timelineService;
        this.qualityControlService = qualityControlService;
        this.logs = logs;
        this.json = json;
    }

    @Override
    public AgentToolBundle create(Long sessionId, CurrentUserVO user, String traceId) {
        AgentToolBundle bundle = new AgentToolBundle(new ToolCallback[0]);
        CommonTools common = new CommonTools(sessionId, user, traceId, bundle);
        List<ToolCallback> callbacks = new ArrayList<>(Arrays.asList(ToolCallbacks.from(common)));
        if (user.getRoleCode() == UserRole.DOCTOR) {
            callbacks.addAll(Arrays.asList(ToolCallbacks.from(new ClinicalTools(sessionId, user, traceId, bundle))));
        } else if (user.getRoleCode() == UserRole.ADMIN) {
            callbacks.addAll(Arrays.asList(ToolCallbacks.from(new GovernanceTools(sessionId, user, traceId, bundle))));
        }
        bundle.setCallbacks(callbacks.toArray(ToolCallback[]::new));
        return bundle;
    }

    private class CommonTools extends AuditedTools {
        CommonTools(Long sessionId, CurrentUserVO user, String traceId, AgentToolBundle bundle) {
            super(sessionId, user, traceId, bundle);
        }

        @Tool(name = "searchMedicalKnowledge", description = "检索 RetinaVision 已启用的医疗与系统知识文档，只用于资料解释")
        public String searchMedicalKnowledge(@ToolParam(description = "需要检索的明确问题") String question) {
            return execute("searchMedicalKnowledge", Map.of("questionLength", safeLength(question)), () -> {
                List<Document> documents = audiencePolicy.filter(
                        retriever.retrieve(new Query(question)), user.getRoleCode());
                List<Map<String, Object>> contexts = documents.stream().map(document -> {
                    AgentCitationVO citation = citation(document);
                    bundle.addCitation(citation);
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("chunkId", citation.chunkId());
                    value.put("title", citation.documentTitle());
                    value.put("source", citation.source());
                    value.put("text", document.getText());
                    value.put("score", citation.score());
                    return value;
                }).toList();
                return Map.of("contexts", contexts, "sufficient", !contexts.isEmpty());
            });
        }

    }

    private class GovernanceTools extends AuditedTools {
        GovernanceTools(Long sessionId, CurrentUserVO user, String traceId, AgentToolBundle bundle) {
            super(sessionId, user, traceId, bundle);
        }

        @Tool(name = "getQualityControlOverview", description = "读取平台 AI 质控总览统计，不返回患者身份信息")
        public String getQualityControlOverview() {
            return execute("getQualityControlOverview", Map.of(), qualityControlService::overview);
        }

        @Tool(name = "listQualityRiskAlerts", description = "读取平台需要管理员关注的 AI 质控风险任务")
        public String listQualityRiskAlerts() {
            return execute("listQualityRiskAlerts", Map.of(), qualityControlService::riskAlerts);
        }
    }

    private class ClinicalTools extends AuditedTools {
        ClinicalTools(Long sessionId, CurrentUserVO user, String traceId, AgentToolBundle bundle) {
            super(sessionId, user, traceId, bundle);
        }

        @Tool(name = "getAssignedCaseSummary", description = "按病例号、匿名患者编号或内部病例 ID 查询当前医生负责的匿名病例摘要")
        public String getAssignedCaseSummary(@ToolParam(description = "病例号、匿名患者编号或内部病例 ID") String caseReference) {
            return execute("getAssignedCaseSummary", Map.of("referenceLength", safeLength(caseReference)), () -> {
                CaseListItemVO item = references.resolveCase(caseReference, user);
                return Map.of(
                        "caseId", item.getId(),
                        "caseNo", item.getCaseNo(),
                          "patientNo", item.getPatientNo(),
                        "patientAge", item.getPatientAge() == null ? "" : item.getPatientAge(),
                        "patientGender", item.getPatientGender(),
                        "eyeSide", item.getEyeSide(),
                        "status", item.getStatus(),
                        "diagnosisNote", item.getDiagnosisNote() == null ? "" : item.getDiagnosisNote());
            });
        }

        @Tool(name = "getAnalysisTask", description = "按任务编号或内部任务 ID 读取当前医生患者的任务详情和状态日志")
        public String getAnalysisTask(@ToolParam(description = "任务编号或内部任务 ID") String taskReference) {
            return execute("getAnalysisTask", Map.of("referenceLength", safeLength(taskReference)), () -> {
                TaskEntity task = references.resolveTask(taskReference, user);
                Integer taskId = task.getId().intValue();
                return Map.of("detail", taskService.getTaskDetail(taskId, user.getId()),
                        "logs", taskService.getTaskLog(taskId, user.getId()));
            });
        }

        @Tool(name = "getCaseAnalysisTimeline", description = "按病例号、匿名患者编号或内部 ID 读取当前医生患者的结构化分析时间线")
        public String getCaseAnalysisTimeline(@ToolParam(description = "病例号、匿名患者编号或内部病例 ID") String caseReference,
                                              @ToolParam(description = "眼别 LEFT、RIGHT、BOTH 或空字符串") String eyeSide,
                                              @ToolParam(description = "任务类型或空字符串") String taskType) {
            return execute("getCaseAnalysisTimeline", Map.of("referenceLength", safeLength(caseReference)), () -> {
                CaseListItemVO item = references.resolveCase(caseReference, user);
                EyeSide eye = enumValue(EyeSide.class, eyeSide);
                TaskType type = enumValue(TaskType.class, taskType);
                return timelineService.timeline(item.getId().longValue(), eye, type, null, null);
            });
        }

        @Tool(name = "compareRecentAnalysisResults", description = "按病例号或匿名患者编号比较当前医生患者最近两次同类分析结果")
        public String compareRecentAnalysisResults(@ToolParam(description = "病例号、匿名患者编号或内部病例 ID") String caseReference,
                                                   @ToolParam(description = "眼别 LEFT、RIGHT、BOTH 或空字符串") String eyeSide,
                                                   @ToolParam(description = "任务类型，默认 VESSEL_SEGMENTATION") String taskType) {
            return execute("compareRecentAnalysisResults", Map.of("referenceLength", safeLength(caseReference)), () -> {
                CaseListItemVO item = references.resolveCase(caseReference, user);
                EyeSide eye = enumValue(EyeSide.class, eyeSide);
                TaskType type = enumValue(TaskType.class, taskType);
                if (type == null) type = TaskType.VESSEL_SEGMENTATION;
                CaseAnalysisTimelineVO timeline = timelineService.timeline(item.getId().longValue(), eye, type, null, null);
                List<Long> resultIds = timeline.items().stream()
                        .map(CaseAnalysisTimelineVO.Item::resultId)
                        .filter(java.util.Objects::nonNull)
                        .toList();
                if (resultIds.size() < 2) {
                    throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "当前病例没有足够的同类历史结果可供比较");
                }
                Long baseline = resultIds.get(resultIds.size() - 2);
                Long target = resultIds.get(resultIds.size() - 1);
                return timelineService.compare(baseline, target);
            });
        }

        @Tool(name = "compareAnalysisResults", description = "比较同一病例、同一眼别、同类任务的两个结构化分析结果")
        public String compareAnalysisResults(@ToolParam(description = "基线结果 ID") Long baselineResultId,
                                             @ToolParam(description = "目标结果 ID") Long targetResultId) {
            return execute("compareAnalysisResults",
                    Map.of("baselineResultId", baselineResultId, "targetResultId", targetResultId), () -> {
                        access.assertCanAccessResult(user, baselineResultId);
                        access.assertCanAccessResult(user, targetResultId);
                        return timelineService.compare(baselineResultId, targetResultId);
                    });
        }
    }

    private abstract class AuditedTools {
        protected final Long sessionId;
        protected final CurrentUserVO user;
        protected final String traceId;
        protected final AgentToolBundle bundle;

        AuditedTools(Long sessionId, CurrentUserVO user, String traceId, AgentToolBundle bundle) {
            this.sessionId = sessionId;
            this.user = user;
            this.traceId = traceId;
            this.bundle = bundle;
        }

        protected String execute(String toolName, Map<String, Object> arguments, Supplier<Object> operation) {
            long started = System.nanoTime();
            try {
                String output = truncate(json.writeValueAsString(operation.get()));
                record(toolName, arguments, output, true, elapsed(started), null);
                return output;
            } catch (BaseException exception) {
                record(toolName, arguments, "", false, elapsed(started), exception.getClass().getSimpleName());
                throw exception;
            } catch (Exception exception) {
                record(toolName, arguments, "", false, elapsed(started), exception.getClass().getSimpleName());
                throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "智能助手工具执行失败");
            }
        }

        private void record(String toolName, Map<String, Object> arguments, String result,
                            boolean success, long latencyMs, String error) {
            AgentToolCallLogEntity entity = new AgentToolCallLogEntity();
            entity.setSessionId(sessionId);
            entity.setUserId(user.getId());
            entity.setTraceId(traceId);
            entity.setToolName(toolName);
            entity.setArgumentsSummary(auditSummary(write(arguments)));
            entity.setResultSummary(success ? auditSummary(result) : null);
            entity.setSuccess(success);
            entity.setLatencyMs(latencyMs);
            entity.setErrorSummary(error);
            entity.setCreatedAt(LocalDateTime.now());
            logs.insert(entity);
            bundle.addSummary(new AgentToolCallSummaryVO(toolName, success, latencyMs));
        }
    }

    private AgentCitationVO citation(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new AgentCitationVO(number(metadata.get("documentId")), number(metadata.get("chunkId")),
                text(metadata.get("documentTitle")), text(metadata.get("source")), document.getText(),
                document.getScore() == null ? 0 : document.getScore());
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "工具参数枚举值无效");
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String truncate(String value) {
        return value != null && value.length() > MAX_TOOL_OUTPUT ? value.substring(0, MAX_TOOL_OUTPUT) : value;
    }

    private String auditSummary(String value) {
        return value != null && value.length() > MAX_AUDIT_SUMMARY
                ? value.substring(0, MAX_AUDIT_SUMMARY)
                : value;
    }

    private long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
