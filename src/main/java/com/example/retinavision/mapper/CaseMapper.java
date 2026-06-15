package com.example.retinavision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retinavision.pojo.DTO.CaseListQueryDTO;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CaseMapper extends BaseMapper<CaseEntity> {

    // SQL 已迁移到 resources/mapper/CaseMapper.xml，Mapper 接口只保留方法签名。
    long countCasePage(@Param("query") CaseListQueryDTO queryDTO);

    List<CaseListItemVO> selectCasePage(
            @Param("query") CaseListQueryDTO queryDTO,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    CaseListItemVO getCaseById(@Param("id") Integer id);
}
