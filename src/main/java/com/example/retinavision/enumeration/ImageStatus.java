package com.example.retinavision.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 图片状态枚举
 */
@Getter
@AllArgsConstructor
public enum ImageStatus {
    
    /** 已上传 - 可用于创建任务 */
    UPLOADED("UPLOADED", "已上传"),
    
    /** 已绑定任务 - 已有关联任务 */
    BOUND_TASK("BOUND_TASK", "已绑定任务"),
    
    /** 已删除 - 不可用于创建任务 */
    DELETED("DELETED", "已删除");
    
    @EnumValue
    @JsonValue
    private final String code;
    
    private final String description;
}
