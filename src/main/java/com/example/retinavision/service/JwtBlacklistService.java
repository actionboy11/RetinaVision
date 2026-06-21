package com.example.retinavision.service;

import java.time.Duration;

public interface JwtBlacklistService {
    boolean isBlacklisted(String jti);
    void blacklist(String jti, Duration remainingLifetime);
}
