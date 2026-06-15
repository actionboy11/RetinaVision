package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.EyeSide;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CaseListQueryDTO {
    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private CaseStatus status;
    private EyeSide eyeSide;
}
