package com.example.retinavision.service.agent;

import com.example.retinavision.pojo.analysis.entity.TaskEntity;
import com.example.retinavision.pojo.cases.vo.CaseListItemVO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;

public interface AgentClinicalReferenceService {
    CaseListItemVO resolveCase(String reference, CurrentUserVO doctor);

    TaskEntity resolveTask(String reference, CurrentUserVO doctor);
}
