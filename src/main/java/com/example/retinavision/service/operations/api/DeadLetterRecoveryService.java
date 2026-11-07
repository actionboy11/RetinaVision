package com.example.retinavision.service.operations.api;

public interface DeadLetterRecoveryService {

    int recover(Integer operatorId, int limit);
}
