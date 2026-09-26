package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.CurrentUserVO;

//  ClinicalAccessService 用于检查当前用户是否有权限访问临床数据
public interface ClinicalAccessService {
    void assertClinicalRole(CurrentUserVO user);
    void assertCanAccessCase(CurrentUserVO user, Long caseId);
    void assertCanAccessImage(CurrentUserVO user, Long imageId);
    void assertCanAccessTask(CurrentUserVO user, Long taskId);
    void assertCanAccessResult(CurrentUserVO user, Long resultId);
    void assertCanModifyCase(CurrentUserVO user, Long caseId);
    void assertCanModifyImage(CurrentUserVO user, Long imageId);
}
