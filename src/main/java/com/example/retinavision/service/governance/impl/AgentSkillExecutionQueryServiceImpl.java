package com.example.retinavision.service.governance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.mapper.governance.AgentSkillExecutionLogMapper;
import com.example.retinavision.pojo.governance.entity.AgentSkillExecutionLogEntity;
import com.example.retinavision.pojo.governance.vo.AgentSkillExecutionVO;
import com.example.retinavision.service.governance.AgentSkillExecutionQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentSkillExecutionQueryServiceImpl implements AgentSkillExecutionQueryService {
    private final AgentSkillExecutionLogMapper mapper;

    public AgentSkillExecutionQueryServiceImpl(AgentSkillExecutionLogMapper mapper) { this.mapper = mapper; }

    @Override
    public List<AgentSkillExecutionVO> latest(String skillCode, Boolean success) {
        LambdaQueryWrapper<AgentSkillExecutionLogEntity> query = new LambdaQueryWrapper<>();
        query.eq(skillCode != null && !skillCode.isBlank(), AgentSkillExecutionLogEntity::getSkillCode, skillCode)
                .eq(success != null, AgentSkillExecutionLogEntity::getSuccess, success)
                .orderByDesc(AgentSkillExecutionLogEntity::getCreatedAt).last("LIMIT 100");
        return mapper.selectList(query).stream().map(item -> new AgentSkillExecutionVO(item.getId(),
                item.getSkillCode(), item.getSkillVersion(), item.getConfidence(), item.getSuccess(),
                item.getLatencyMs(), item.getErrorType(), item.getCreatedAt())).toList();
    }
}
