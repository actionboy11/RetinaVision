package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.CaseInsertDTO;
import com.example.retinavision.pojo.DTO.CaseDoctorAssignmentDTO;
import com.example.retinavision.pojo.DTO.CaseListQueryDTO;
import com.example.retinavision.pojo.DTO.CaseUpdateDTO;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.PageResult;

public interface CaseService {
    PageResult<CaseListItemVO> getCaseList(CaseListQueryDTO queryDTO, CurrentUserVO user);

    CaseListItemVO addCase(CaseInsertDTO caseInsertDTO, CurrentUserVO user);

    CaseListItemVO assignDoctor(Integer caseId, CaseDoctorAssignmentDTO request, CurrentUserVO user);

    CaseListItemVO getCaseById(Integer caseId);

    CaseListItemVO updateCase(Integer caseId, CaseUpdateDTO caseUpdateDTO, CurrentUserVO user);

    void deleteCase(Integer caseId, CurrentUserVO user);

    CaseListItemVO submit(Integer caseId, CurrentUserVO user);

    CaseListItemVO withdraw(Integer caseId, CurrentUserVO user);
}
