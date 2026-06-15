package com.example.retinavision.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 分析任务状态枚举
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {
    
    /** 已创建 - 任务已入库 */
    CREATED("CREATED", "已创建"),
    
    /** 排队中 - 等待 Worker 消费 */
    WAITING("WAITING", "排队中"),
    
    /** 运行中 - Worker 正在处理 */
    RUNNING("RUNNING", "运行中"),
    
    /** 成功 - 有分析结果 */
    SUCCESS("SUCCESS", "成功"),
    
    /** 失败 - 可查看错误原因，可重试 */
    FAILED("FAILED", "失败"),
    
    /** 重试中 - 正在重新投递或重新排队 */
    RETRYING("RETRYING", "重试中"),
    
    /** 已取消 - 已取消，不再处理 */
    CANCELED("CANCELED", "已取消");
    
    @EnumValue
    @JsonValue
    private final String code;
    
    private final String description;
}
