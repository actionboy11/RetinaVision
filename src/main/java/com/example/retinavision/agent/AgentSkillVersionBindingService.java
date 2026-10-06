package com.example.retinavision.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AgentSessionSkillVersionMapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.AgentSessionSkillVersionEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillVersionEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AgentSkillVersionBindingService {
    private final AgentSessionSkillVersionMapper bindings;
    private final AgentSkillMapper skills;
    private final AgentSkillVersionMapper versions;

    public AgentSkillVersionBindingService(AgentSessionSkillVersionMapper bindings, AgentSkillMapper skills,
                                           AgentSkillVersionMapper versions) {
        this.bindings = bindings;
        this.skills = skills;
        this.versions = versions;
    }

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
