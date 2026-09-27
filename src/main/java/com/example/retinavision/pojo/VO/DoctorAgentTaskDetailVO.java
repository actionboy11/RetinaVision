package com.example.retinavision.pojo.VO;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class DoctorAgentTaskDetailVO extends DoctorAgentTaskSummaryVO {
    private List<DoctorAgentTaskLogVO> logs = List.of();
}
