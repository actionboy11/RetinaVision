package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.CurrentUserVO;

public interface ClinicalAccessService {
    void assertClinicalRole(CurrentUserVO user);
    void assertCanAccessCase(CurrentUserVO user, Long caseId);
    void assertCanAccessImage(CurrentUserVO user, Long imageId);
    void assertCanAccessTask(CurrentUserVO user, Long taskId);
    void assertCanAccessResult(CurrentUserVO user, Long resultId);
}
