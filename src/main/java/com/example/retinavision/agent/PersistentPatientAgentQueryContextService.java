package com.example.retinavision.agent;

import com.example.retinavision.mapper.AgentQueryContextMapper;
import com.example.retinavision.pojo.Entity.AgentQueryContextEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PersistentPatientAgentQueryContextService implements PatientAgentQueryContextService {
    private static final Logger log = LoggerFactory.getLogger(PersistentPatientAgentQueryContextService.class);
    private static final int EXPIRY_HOURS = 2;
    private static final Set<AgentSkillCode> PATIENT_SKILLS = Set.of(
            AgentSkillCode.MY_CASE_LIST, AgentSkillCode.MY_CASE_PROGRESS,
            AgentSkillCode.MY_SIGNED_REPORT, AgentSkillCode.PATIENT_KNOWLEDGE_QA);

    private final AgentQueryContextMapper mapper;
    private final ObjectMapper json;

    public PersistentPatientAgentQueryContextService(AgentQueryContextMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public Optional<PatientAgentQueryContextSnapshot> load(Long sessionId) {
        AgentQueryContextEntity entity = mapper.selectById(sessionId);
        if (entity == null || entity.getExpiresAt() == null
                || entity.getExpiresAt().isBefore(LocalDateTime.now())) return Optional.empty();
        try {
            AgentSkillCode skill = AgentSkillCode.valueOf(entity.getCurrentSkillCode());
            if (!PATIENT_SKILLS.contains(skill)) return Optional.empty();
            Map<String, Object> filters = entity.getCurrentFiltersJson() == null ? Map.of()
                    : json.readValue(entity.getCurrentFiltersJson(), new TypeReference<>() {});
            List<PatientAgentReference> references = entity.getRecentResultReferencesJson() == null ? List.of()
                    : json.readValue(entity.getRecentResultReferencesJson(), new TypeReference<>() {});
            Long selectedCaseId = entity.getSelectedCaseId() == null
                    ? longValue(filters.get("selectedCaseId"))
                    : Long.valueOf(entity.getSelectedCaseId());
            return Optional.of(new PatientAgentQueryContextSnapshot(
                    skill,
                    booleanValue(filters.get("reuploadOnly")),
                    booleanValue(filters.get("signedReportOnly")),
                    value(entity.getCurrentPage(), 1), value(entity.getPageSize(), 10),
                    entity.getTotal() == null ? 0 : entity.getTotal(),
                    selectedCaseId,
                    longValue(filters.get("selectedResultId")),
                    intValue(filters.get("selectedReportVersion")),
                    references));
        } catch (Exception exception) {
            log.warn("Unable to restore patient Agent context sessionId={} errorType={}",
                    sessionId, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void save(Long sessionId, PatientAgentQueryContextSnapshot context) {
        try {
            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("reuploadOnly", context.reuploadOnly());
            filters.put("signedReportOnly", context.signedReportOnly());
            if (context.selectedCaseId() != null) filters.put("selectedCaseId", context.selectedCaseId());
            if (context.selectedResultId() != null) filters.put("selectedResultId", context.selectedResultId());
            if (context.selectedReportVersion() != null) {
                filters.put("selectedReportVersion", context.selectedReportVersion());
            }
            AgentQueryContextEntity entity = new AgentQueryContextEntity();
            entity.setSessionId(sessionId);
            entity.setCurrentSkillCode(context.currentSkill().name());
            entity.setReferenceType(context.currentSkill() == AgentSkillCode.MY_SIGNED_REPORT ? "REPORT" : "CASE");
            entity.setCurrentFiltersJson(json.writeValueAsString(filters));
            entity.setCurrentPage(context.page());
            entity.setPageSize(Math.min(context.pageSize(), 10));
            entity.setTotal(context.total());
            if (context.selectedCaseId() != null) {
                entity.setSelectedCaseId(Math.toIntExact(context.selectedCaseId()));
            }
            entity.setRecentResultReferencesJson(json.writeValueAsString(
                    context.references().stream().limit(10).toList()));
            entity.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));
            entity.setUpdatedAt(LocalDateTime.now());
            if (mapper.selectById(sessionId) == null) mapper.insert(entity); else mapper.updateById(entity);
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存患者智能助手查询上下文", exception);
        }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
