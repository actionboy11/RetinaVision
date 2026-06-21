package com.example.retinavision.service;

import java.time.Duration;

/**
 * JWT 黑名单服务接口，定义了检查和添加 JWT 到黑名单的方法。
 * 该接口的实现类将负责管理 JWT 黑名单的检查和添加逻辑
 */
public interface JwtBlacklistService {
    boolean isBlacklisted(String jti);
    void blacklist(String jti, Duration remainingLifetime);
}
