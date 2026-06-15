package com.example.retinavision.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 眼别枚举
 */
@Getter
@AllArgsConstructor
public enum EyeSide {
    
    /** 左眼 */
    LEFT("LEFT", "左眼"),
    
    /** 右眼 */
    RIGHT("RIGHT", "右眼"),
    
    /** 双眼 */
    BOTH("BOTH", "双眼");
    
    @EnumValue
    @JsonValue
    private final String code;
    
    private final String description;
}
