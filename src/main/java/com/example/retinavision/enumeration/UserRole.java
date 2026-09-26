package com.example.retinavision.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
@AllArgsConstructor
public enum UserRole {
    
    /** 管理员 */
    ADMIN("ADMIN", "管理员"),
    
    /** 患者账号；保留 USER 枚举值以兼容历史 JWT 与数据库。 */
    USER("USER", "患者"),
    
    /** 医生 */
    DOCTOR("DOCTOR", "医生"),
    
    /** 研究员 */
    RESEARCHER("RESEARCHER", "研究员");
    
    @EnumValue
    @JsonValue
    private final String code;
    
    private final String description;
}
