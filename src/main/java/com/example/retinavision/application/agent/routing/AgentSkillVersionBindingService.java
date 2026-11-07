package com.example.retinavision.application.agent.routing;

import com.example.retinavision.application.agent.routing.api.AgentSkillVersionResolver;
import com.example.retinavision.constant.shared.ErrorMessageSignal;
import com.example.retinavision.pojo.agent.AgentSkillCode;
import com.example.retinavision.pojo.agent.AgentSkillRuntimeVersion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.exception.shared.BaseException;
import com.example.retinavision.mapper.agent.AgentSessionSkillVersionMapper;
import com.example.retinavision.mapper.agent.AgentSkillMapper;
import com.example.retinavision.mapper.agent.AgentSkillVersionMapper;
import com.example.retinavision.pojo.agent.entity.AgentSessionSkillVersionEntity;
import com.example.retinavision.pojo.agent.entity.AgentSkillEntity;
import com.example.retinavision.pojo.agent.entity.AgentSkillVersionEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AgentSkillVersionBindingService implements AgentSkillVersionResolver {
    private final AgentSessionSkillVersionMapper bindings;
    private final AgentSkillMapper skills;
    private final AgentSkillVersionMapper versions;

    public AgentSkillVersionBindingService(AgentSessionSkillVersionMapper bindings, AgentSkillMapper skills,
                                           AgentSkillVersionMapper versions) {
        this.bindings = bindings;
        this.skills = skills;
        this.versions = versions;
    }

    @Override
    public AgentSkillRuntimeVersion resolve(Long sessionId, AgentSkillCode skillCode) {
        AgentSessionSkillVersionEntity binding = bindings.selectOne(
                new LambdaQueryWrapper<AgentSessionSkillVersionEntity>()
                        .eq(AgentSessionSkillVersionEntity::getSessionId, sessionId)
                        .eq(AgentSessionSkillVersionEntity::getSkillCode, skillCode.name()));
        if (binding != null) return runtimeVersion(binding.getSkillVersionId());

        AgentSkillEntity skill = skills.selectOne(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getSkillCode, skillCode.name())
                .eq(AgentSkillEntity::getStatus, "ACTIVE"));
        if (skill == null || skill.getActiveVersionId() == null) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "当前 Skill 没有可用版本");
        }
        AgentSkillRuntimeVersion version = runtimeVersion(skill.getActiveVersionId());
        AgentSessionSkillVersionEntity created = new AgentSessionSkillVersionEntity();
        created.setSessionId(sessionId);
        created.setSkillCode(skillCode.name());
        created.setSkillVersionId(version.versionId());
        created.setCreatedAt(LocalDateTime.now());
        bindings.insert(created);
        return version;
    }

    public Optional<Long> findVersionId(Long sessionId, AgentSkillCode skillCode) {
        AgentSessionSkillVersionEntity binding = bindings.selectOne(
                new LambdaQueryWrapper<AgentSessionSkillVersionEntity>()
                        .eq(AgentSessionSkillVersionEntity::getSessionId, sessionId)
                        .eq(AgentSessionSkillVersionEntity::getSkillCode, skillCode.name()));
        return binding == null ? Optional.empty() : Optional.of(binding.getSkillVersionId());
    }

    private AgentSkillRuntimeVersion runtimeVersion(Long versionId) {
        AgentSkillVersionEntity version = versions.selectById(versionId);
        if (version == null) {
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "Skill 版本配置不存在");
        }
        return new AgentSkillRuntimeVersion(version.getId(), version.getVersion());
    }
}
