package com.example.retinavision.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillVersionEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AgentSkillCatalogService {
    private final AgentSkillMapper skills;
    private final AgentSkillVersionMapper versions;
    private final AgentSkillVersionBindingService bindings;
    private final AgentSkillRegistry registry;

    public AgentSkillCatalogService(AgentSkillMapper skills, AgentSkillVersionMapper versions,
                                    AgentSkillVersionBindingService bindings, AgentSkillRegistry registry) {
        this.skills = skills;
        this.versions = versions;
        this.bindings = bindings;
        this.registry = registry;
    }

    public List<AgentSkillDefinition> availableFor(Long sessionId, CurrentUserVO user) {
        List<AgentSkillEntity> active = skills.selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getStatus, "ACTIVE").orderByAsc(AgentSkillEntity::getId));
        List<AgentSkillDefinition> result = new ArrayList<>();
        for (AgentSkillEntity skill : active) {
            AgentSkillCode code = parseCode(skill.getSkillCode());
            if (code == null || user == null || !registry.isAvailable(code, user.getRoleCode())) continue;
            Long versionId = bindings.findVersionId(sessionId, code).orElse(skill.getActiveVersionId());
            if (versionId == null) continue;
            AgentSkillVersionEntity version = versions.selectById(versionId);
            if (version != null) result.add(definition(skill, version));
        }
        return List.copyOf(result);
    }

    public List<AgentSkillDefinition> forEvaluation(AgentSkillEntity candidateSkill,
                                                    AgentSkillVersionEntity candidateVersion) {
        List<AgentSkillEntity> all = skills.selectList(
                new LambdaQueryWrapper<AgentSkillEntity>().orderByAsc(AgentSkillEntity::getId));
        List<AgentSkillDefinition> result = new ArrayList<>();
        for (AgentSkillEntity skill : all) {
            AgentSkillCode code = parseCode(skill.getSkillCode());
            if (code == null || !registry.isAvailable(code, com.example.retinavision.enumeration.UserRole.DOCTOR)) {
                continue;
            }
            AgentSkillVersionEntity version;
            if (Objects.equals(skill.getId(), candidateSkill.getId())) {
                version = candidateVersion;
            } else {
                if (!"ACTIVE".equals(skill.getStatus()) || skill.getActiveVersionId() == null) continue;
                version = versions.selectById(skill.getActiveVersionId());
            }
            if (version != null) result.add(definition(skill, version));
        }
        if (result.stream().noneMatch(item -> item.code().name().equals(candidateSkill.getSkillCode()))) {
            AgentSkillCode code = parseCode(candidateSkill.getSkillCode());
            if (code != null && registry.isAvailable(code, com.example.retinavision.enumeration.UserRole.DOCTOR)) {
                result.add(definition(candidateSkill, candidateVersion));
            }
        }
        return List.copyOf(result);
    }

    AgentSkillDefinition definition(AgentSkillEntity skill, AgentSkillVersionEntity version) {
        return new AgentSkillDefinition(AgentSkillCode.valueOf(skill.getSkillCode()), skill.getName(),
                skill.getDescription(), version.getVersion(), version.getRoutingExamplesJson(),
                version.getWorkflowPrompt());
    }

    private AgentSkillCode parseCode(String value) {
        try { return AgentSkillCode.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException ignored) { return null; }
    }
}
