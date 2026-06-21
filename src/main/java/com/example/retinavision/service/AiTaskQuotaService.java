package com.example.retinavision.service;

public interface AiTaskQuotaService {
    AiQuotaReservation reserve(Integer userId);
    void release(AiQuotaReservation reservation);
}
