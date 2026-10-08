package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.mapper.LlmCallLogMapper;
import com.example.retinavision.pojo.Entity.LlmCallLogEntity;
import com.example.retinavision.service.LlmCallLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LlmCallLogServiceImpl implements LlmCallLogService {
    private final LlmCallLogMapper logs;

    public LlmCallLogServiceImpl(LlmCallLogMapper logs) {
        this.logs = logs;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LlmCallLogEntity log) {
        logs.insert(log);
    }

    @Override
    public List<LlmCallLogEntity> list(String scenario,
                                       String templateCode,
                                       Boolean success,
                                       LocalDateTime startTime,
                                       LocalDateTime endTime) {
        return logs.selectList(new LambdaQueryWrapper<LlmCallLogEntity>()
                .eq(LlmCallLogEntity::getCallSource, "BUSINESS")
                .eq(scenario != null && !scenario.isBlank(), LlmCallLogEntity::getScenario, scenario)
                .eq(templateCode != null && !templateCode.isBlank(), LlmCallLogEntity::getTemplateCode, templateCode)
                .eq(success != null, LlmCallLogEntity::getSuccess, success)
                .ge(startTime != null, LlmCallLogEntity::getCreatedAt, startTime)
                .le(endTime != null, LlmCallLogEntity::getCreatedAt, endTime)
                .orderByDesc(LlmCallLogEntity::getCreatedAt)
                .last("LIMIT 200"));
    }
}
