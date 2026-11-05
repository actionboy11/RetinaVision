package com.example.retinavision.service.operations;

public interface DeadLetterRecoveryService {

    int recover(Integer operatorId, int limit);
}
