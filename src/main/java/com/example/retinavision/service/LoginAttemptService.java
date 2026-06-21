package com.example.retinavision.service;

public interface LoginAttemptService {
    void assertAllowed(String username, String clientIp);
    void recordFailure(String username, String clientIp);
    void recordSuccess(String username);
}
