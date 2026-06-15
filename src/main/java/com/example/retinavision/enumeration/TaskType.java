package com.example.retinavision.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 分析任务类型枚举
 */
@Getter
@AllArgsConstructor
public enum TaskType {
    
    /** 血管分割 - 生成 mask 图和血管指标 */
    VESSEL_SEGMENTATION("VESSEL_SEGMENTATION", "血管分割"),
    
    /** 图像质量检测 - 生成质量评分指标 */
    IMAGE_QUALITY_CHECK("IMAGE_QUALITY_CHECK", "图像质量检测");
    
    @EnumValue
    @JsonValue
    private final String code;
    
    private final String description;
}
