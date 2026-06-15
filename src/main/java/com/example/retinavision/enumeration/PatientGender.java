package com.example.retinavision.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 患者性别枚举
 */
@Getter
@AllArgsConstructor
public enum PatientGender {
    
    /** 男性 */
    MALE("MALE", "男性"),
    
    /** 女性 */
    FEMALE("FEMALE", "女性"),
    
    /** 未知 */
    UNKNOWN("UNKNOWN", "未知");
    
    @EnumValue
    @JsonValue
    private final String code;
    
    private final String description;
}
