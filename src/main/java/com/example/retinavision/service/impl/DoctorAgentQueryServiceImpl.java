package com.example.retinavision.service.impl;

import com.example.retinavision.agent.DoctorCaseSearchCriteria;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.DoctorAgentQueryMapper;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.DoctorAgentCaseSummaryVO;
import com.example.retinavision.pojo.VO.DoctorWorkloadVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.service.DoctorAgentQueryService;
import org.springframework.stereotype.Service;

@Service
public class DoctorAgentQueryServiceImpl implements DoctorAgentQueryService {
    private static final int PAGE_SIZE = 10;
    private final DoctorAgentQueryMapper mapper;

    public DoctorAgentQueryServiceImpl(DoctorAgentQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DoctorWorkloadVO getClinicalWorkload(CurrentUserVO doctor) {
        requireDoctor(doctor);
        DoctorWorkloadVO value = mapper.selectWorkload(doctor.getId());
        return value == null ? new DoctorWorkloadVO(0, 0, 0, 0, 0, 0) : value;
    }

    @Override
    public PageResult<DoctorAgentCaseSummaryVO> searchAssignedCases(DoctorCaseSearchCriteria criteria,
                                                                    Integer page,
                                                                    Integer pageSize,
                                                                    CurrentUserVO doctor) {
        requireDoctor(doctor);
        DoctorCaseSearchCriteria safeCriteria = criteria == null
                ? new DoctorCaseSearchCriteria(null, null) : criteria;
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? PAGE_SIZE : Math.min(pageSize, PAGE_SIZE);
        long total = mapper.countAssignedCases(doctor.getId(), safeCriteria);
        var records = mapper.selectAssignedCases(doctor.getId(), safeCriteria,
                (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(records, total, safePage, safeSize);
    }

    private void requireDoctor(CurrentUserVO doctor) {
        if (doctor == null || doctor.getRoleCode() != UserRole.DOCTOR) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权使用医生病例查询能力");
        }
    }
}
