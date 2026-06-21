package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.CaseInsertDTO;
import com.example.retinavision.pojo.DTO.CaseListQueryDTO;
import com.example.retinavision.pojo.DTO.CaseUpdateDTO;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.PageResult;

public interface CaseService {
    PageResult<CaseListItemVO> getCaseList(CaseListQueryDTO queryDTO, CurrentUserVO user);

    CaseListItemVO addCase(CaseInsertDTO caseInsertDTO, Integer userid);

    CaseListItemVO getCaseById(Integer caseId);

    CaseListItemVO updateCase(Integer caseId, CaseUpdateDTO caseUpdateDTO);

    void deleteCase(Integer caseId);
}
