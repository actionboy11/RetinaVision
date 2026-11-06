package com.example.retinavision.service.governance;

import com.example.retinavision.pojo.governance.entity.LlmCallLogEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface LlmCallLogService {
    void record(LlmCallLogEntity log);

    List<LlmCallLogEntity> list(String scenario,
                                String templateCode,
                                Boolean success,
                                LocalDateTime startTime,
                                LocalDateTime endTime);
}
