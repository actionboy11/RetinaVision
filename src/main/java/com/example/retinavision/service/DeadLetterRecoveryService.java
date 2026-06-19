package com.example.retinavision.service;

public interface DeadLetterRecoveryService {

    int recover(Integer operatorId, int limit);
}
