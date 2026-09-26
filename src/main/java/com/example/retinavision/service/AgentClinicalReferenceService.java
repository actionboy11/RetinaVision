package com.example.retinavision.service;

import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;

public interface AgentClinicalReferenceService {
    CaseListItemVO resolveCase(String reference, CurrentUserVO doctor);

    TaskEntity resolveTask(String reference, CurrentUserVO doctor);
}
