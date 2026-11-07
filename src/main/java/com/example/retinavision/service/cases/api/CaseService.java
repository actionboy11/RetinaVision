package com.example.retinavision.service.cases.api;

import com.example.retinavision.pojo.cases.dto.CaseInsertDTO;
import com.example.retinavision.pojo.cases.dto.CaseDoctorAssignmentDTO;
import com.example.retinavision.pojo.cases.dto.CaseListQueryDTO;
import com.example.retinavision.pojo.cases.dto.CaseUpdateDTO;
import com.example.retinavision.pojo.cases.vo.CaseListItemVO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
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
