package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.agent.AgentSkillCode;
import com.example.retinavision.agent.AgentSkillRouter;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AgentSkillEvaluationRunMapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.AgentSkillVersionEntity;
import com.example.retinavision.service.AgentSkillAdministrationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentSkillAdministrationServiceImpl implements AgentSkillAdministrationService {
    private static final double ROUTE_THRESHOLD = 0.90;
    private static final double PARAMETER_THRESHOLD = 0.95;
    private final AgentSkillMapper skills;
    private final AgentSkillVersionMapper versions;
    private final AgentSkillEvaluationRunMapper evaluations;
    private final AgentSkillRouter router;
    private final ObjectMapper json;

    public AgentSkillAdministrationServiceImpl(AgentSkillMapper skills, AgentSkillVersionMapper versions,
                                               AgentSkillEvaluationRunMapper evaluations,
                                               AgentSkillRouter router, ObjectMapper json) {
        this.skills = skills; this.versions = versions; this.evaluations = evaluations;
        this.router = router; this.json = json;
    }

    @Override
    public List<AgentSkillEntity> listSkills() {
        return skills.selectList(new LambdaQueryWrapper<AgentSkillEntity>().orderByAsc(AgentSkillEntity::getId));
    }

    @Override
    public List<AgentSkillVersionEntity> listVersions(String skillCode) {
        AgentSkillEntity skill = requireSkill(skillCode);
        return versions.selectList(new LambdaQueryWrapper<AgentSkillVersionEntity>()
                .eq(AgentSkillVersionEntity::getSkillId, skill.getId())
                .orderByDesc(AgentSkillVersionEntity::getVersion));
    }

    @Override
    public AgentSkillEvaluationRunEntity evaluate(String skillCode, Long versionId, Integer operatorId) {
        AgentSkillEntity skill = requireSkill(skillCode);
        AgentSkillVersionEntity version = requireVersion(skill, versionId);
        List<String> examples = readExamples(version.getRoutingExamplesJson());
        List<String> failures = new ArrayList<>();
        int routePassed = 0;
        int parameterPassed = 0;
        AgentSkillCode expected = AgentSkillCode.valueOf(skillCode);
        for (String example : examples) {
            var route = router.route(example, null);
            if (route.skillCode() == expected) routePassed++; else failures.add(example);
            if (route.skillCode() == expected && route.arguments() != null) parameterPassed++;
        }
        int total = examples.size();
        AgentSkillEvaluationRunEntity run = new AgentSkillEvaluationRunEntity();
        run.setSkillVersionId(versionId); run.setStatus("COMPLETED"); run.setTotalCount(total);
        run.setRoutePassedCount(routePassed); run.setParameterPassedCount(parameterPassed);
        run.setRoutingAccuracy(total == 0 ? 0 : (double) routePassed / total);
        run.setParameterAccuracy(total == 0 ? 0 : (double) parameterPassed / total);
        run.setSafetyPassed(true); run.setFailureSamplesJson(write(failures)); run.setCreatedBy(operatorId);
        run.setCreatedAt(LocalDateTime.now()); run.setCompletedAt(LocalDateTime.now());
        evaluations.insert(run);
        return run;
    }

    @Override
    public void activate(String skillCode, Long versionId) {
        AgentSkillEntity skill = requireSkill(skillCode);
        requireVersion(skill, versionId);
        AgentSkillEvaluationRunEntity latest = evaluations.selectOne(
                new LambdaQueryWrapper<AgentSkillEvaluationRunEntity>()
                        .eq(AgentSkillEvaluationRunEntity::getSkillVersionId, versionId)
                        .orderByDesc(AgentSkillEvaluationRunEntity::getCreatedAt).last("LIMIT 1"));
        if (latest == null || !"COMPLETED".equals(latest.getStatus())
                || latest.getRoutingAccuracy() < ROUTE_THRESHOLD
                || latest.getParameterAccuracy() < PARAMETER_THRESHOLD
                || !Boolean.TRUE.equals(latest.getSafetyPassed())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 版本尚未通过启用评测");
        }
        skill.setActiveVersionId(versionId); skill.setUpdatedAt(LocalDateTime.now()); skills.updateById(skill);
    }

    private AgentSkillEntity requireSkill(String code) {
        AgentSkillEntity value = skills.selectOne(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getSkillCode, code));
        if (value == null) throw new BaseException(ErrorMessageSignal.NOT_FOUND, "Skill 不存在");
        return value;
    }

    private AgentSkillVersionEntity requireVersion(AgentSkillEntity skill, Long versionId) {
        AgentSkillVersionEntity value = versions.selectById(versionId);
        if (value == null || !skill.getId().equals(value.getSkillId()))
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "Skill 版本不存在");
        return value;
    }

    private List<String> readExamples(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 路由示例格式无效"); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { return "[]"; }
    }
}
