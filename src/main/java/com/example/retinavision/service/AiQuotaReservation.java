package com.example.retinavision.service;

/**
 * AI 配额预约记录，包含分钟级和日级的配额键。
 * 该记录用于存储用户在不同时间范围内的 AI 配额预约信息，确保在指定时间内不会超过配额限制。
 */
//record 是 Java 14 引入的一种特殊类型，用于定义不可变的数据载体类。
// 它自动生成构造函数、访问器方法、equals、hashCode 和 toString 方法，简化了数据类的定义。
public record AiQuotaReservation(String minuteKey, String dayKey) {
}
