package com.example.retinavision.agent;

import com.example.retinavision.mapper.AgentQueryContextMapper;
import com.example.retinavision.pojo.Entity.AgentQueryContextEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PersistentAgentQueryContextService implements AgentQueryContextService {
    private static final int EXPIRY_HOURS = 2;
    private final AgentQueryContextMapper mapper;
    private final ObjectMapper json;

    public PersistentAgentQueryContextService(AgentQueryContextMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public Optional<AgentQueryContextSnapshot> load(Long sessionId) {
        AgentQueryContextEntity entity = mapper.selectById(sessionId);
        if (entity == null || entity.getExpiresAt().isBefore(LocalDateTime.now())) return Optional.empty();
        try {
            Map<String, String> filters = entity.getCurrentFiltersJson() == null ? Map.of()
                    : json.readValue(entity.getCurrentFiltersJson(), new TypeReference<>() {});
            List<Integer> references = entity.getRecentResultReferencesJson() == null ? List.of()
                    : json.readValue(entity.getRecentResultReferencesJson(), new TypeReference<>() {});
            return Optional.of(new AgentQueryContextSnapshot(
                    AgentSkillCode.valueOf(entity.getCurrentSkillCode()),
                    SegmentationState.valueOf(filters.getOrDefault("segmentationState", "ANY")),
                    entity.getCurrentPage(), entity.getPageSize(), entity.getTotal(),
                    entity.getSelectedCaseId(), entity.getSelectedTaskId(), references));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void save(Long sessionId, AgentQueryContextSnapshot context) {
        try {
            AgentQueryContextEntity entity = new AgentQueryContextEntity();
            entity.setSessionId(sessionId);
            entity.setCurrentSkillCode(context.currentSkill().name());
            entity.setCurrentFiltersJson(json.writeValueAsString(Map.of(
                    "segmentationState", context.segmentationState().name())));
            entity.setCurrentPage(context.page());
            entity.setPageSize(Math.min(context.pageSize(), 10));
            entity.setTotal(context.total());
            entity.setSelectedCaseId(context.selectedCaseId());
            entity.setSelectedTaskId(context.selectedTaskId());
            entity.setRecentResultReferencesJson(json.writeValueAsString(context.recentCaseIds()));
            entity.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));
            entity.setUpdatedAt(LocalDateTime.now());
            if (mapper.selectById(sessionId) == null) mapper.insert(entity); else mapper.updateById(entity);
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存智能助手查询上下文", exception);
        }
    }
}
