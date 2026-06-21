package com.example.retinavision.service.impl;

import com.example.retinavision.exception.RedisUnavailableException;
import com.example.retinavision.redis.RedisKeyFactory;
import com.example.retinavision.service.JwtBlacklistService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
/**
 * Redis 实现的 JWT 黑名单服务，用于检查和添加 JWT 到黑名单。
 * 该服务将负责管理 JWT 黑名单的检查和添加逻辑
 */
public class RedisJwtBlacklistService implements JwtBlacklistService {

    // RedisTemplate 用于与 Redis 进行交互，提供了对 Redis 数据库的操作方法
    private final StringRedisTemplate redisTemplate;
    // RedisKeyFactory 用于生成 Redis 键，确保键的命名规范和一致性
    private final RedisKeyFactory keyFactory;

    public RedisJwtBlacklistService(StringRedisTemplate redisTemplate, RedisKeyFactory keyFactory) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
    }

    @Override
    // 检查 JWT 是否在黑名单中
    // 如果 JWT 存在于黑名单中，返回 true；否则返回 false
    public boolean isBlacklisted(String jti) {
        try {
            //hasKey 方法用于检查 Redis 中是否存在指定的键，keyFactory.jwtBlacklist(jti) 生成 JWT 黑名单键，jti 是 JWT 的唯一标识符
            return Boolean.TRUE.equals(redisTemplate.hasKey(keyFactory.jwtBlacklist(jti)));
        } catch (DataAccessException exception) {
            throw new RedisUnavailableException("认证服务暂时不可用，请稍后重试", exception);
        }
    }

    @Override
    // 将 JWT 添加到黑名单中，并设置其剩余有效期
    // 如果剩余有效期为 null、零或负数，不执行任何操作
    public void blacklist(String jti, Duration remainingLifetime) {
        if (remainingLifetime == null || remainingLifetime.isZero() || remainingLifetime.isNegative()) {return;}
        try {
            // 将 JWT 添加到黑名单中，使用 Redis 的 set 方法，并设置过期时间为 remainingLifetime
            //redisTemplate.opsForValue().set(keyFactory.jwtBlacklist(jti), "1", remainingLifetime) 将键值对存储在 Redis 中，
            // 其中键是通过 keyFactory 生成的 JWT 黑名单键，值为 "1"，过期时间为 remainingLifetime
            redisTemplate.opsForValue().set(keyFactory.jwtBlacklist(jti), "1", remainingLifetime);
        } catch (DataAccessException exception) {
            throw new RedisUnavailableException("认证服务暂时不可用，请稍后重试", exception);
        }
    }
}
