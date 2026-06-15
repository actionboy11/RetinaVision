package com.example.retinavision.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 病例状态枚举
 */
@Getter
@AllArgsConstructor
public enum CaseStatus {
    
    /** 活跃 - 正常使用的病例 */
    ACTIVE("ACTIVE", "活跃"),
    
    /** 已归档 - 已归档的病例 */
    ARCHIVED("ARCHIVED", "已归档"),
    
    /** 已删除 - 已删除的病例 */
    DELETED("DELETED", "已删除");
    
    @EnumValue
    @JsonValue
    private final String code;
    
    private final String description;
}
