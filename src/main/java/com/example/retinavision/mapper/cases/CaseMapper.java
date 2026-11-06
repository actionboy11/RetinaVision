package com.example.retinavision.mapper.cases;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retinavision.pojo.cases.dto.CaseListQueryDTO;
import com.example.retinavision.pojo.cases.entity.CaseEntity;
import com.example.retinavision.pojo.cases.vo.CaseListItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CaseMapper extends BaseMapper<CaseEntity> {

    // SQL 已迁移到 resources/mapper/CaseMapper.xml，Mapper 接口只保留方法签名。
    long countCasePage(@Param("query") CaseListQueryDTO queryDTO,
                       @Param("patientAccountUserId") Integer patientAccountUserId,
                       @Param("doctorId") Integer doctorId);

    List<CaseListItemVO> selectCasePage(
            @Param("query") CaseListQueryDTO queryDTO,
            @Param("patientAccountUserId") Integer patientAccountUserId,
            @Param("doctorId") Integer doctorId,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    CaseListItemVO getCaseById(@Param("id") Integer id);

    CaseListItemVO findAssignedCaseByReference(@Param("reference") String reference,
                                               @Param("numericId") Long numericId,
                                               @Param("doctorId") Integer doctorId);
}
