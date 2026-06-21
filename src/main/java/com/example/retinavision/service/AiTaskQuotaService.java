package com.example.retinavision.service;

/**
 * AI 任务配额服务接口，定义了预约和释放 AI 任务配额的方法。
 * 该接口的实现类将负责管理 AI 任务配额的预约和释放逻辑
 */
public interface AiTaskQuotaService {
    AiQuotaReservation reserve(Integer userId);
    void release(AiQuotaReservation reservation);
}
