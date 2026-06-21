package com.example.retinavision.service.impl;

import com.example.retinavision.exception.RedisUnavailableException;
import com.example.retinavision.redis.RedisKeyFactory;
import com.example.retinavision.service.JwtBlacklistService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisJwtBlacklistService implements JwtBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;

    public RedisJwtBlacklistService(StringRedisTemplate redisTemplate, RedisKeyFactory keyFactory) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
    }

    @Override
    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(keyFactory.jwtBlacklist(jti)));
        } catch (DataAccessException exception) {
            throw new RedisUnavailableException("认证服务暂时不可用，请稍后重试", exception);
        }
    }

    @Override
    public void blacklist(String jti, Duration remainingLifetime) {
        if (remainingLifetime == null || remainingLifetime.isZero() || remainingLifetime.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(keyFactory.jwtBlacklist(jti), "1", remainingLifetime);
        } catch (DataAccessException exception) {
            throw new RedisUnavailableException("认证服务暂时不可用，请稍后重试", exception);
        }
    }
}
