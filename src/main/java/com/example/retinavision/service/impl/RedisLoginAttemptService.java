package com.example.retinavision.service.impl;

import com.example.retinavision.config.RedisFeatureProperties;
import com.example.retinavision.exception.RateLimitException;
import com.example.retinavision.redis.RedisKeyFactory;
import com.example.retinavision.service.LoginAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptService.class);
    private static final DefaultRedisScript<List> CHECK_SCRIPT = new DefaultRedisScript<>("""
            local userCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            local ipCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if userCount >= tonumber(ARGV[1]) or ipCount >= tonumber(ARGV[2]) then
                local ttl = math.max(redis.call('PTTL', KEYS[1]), redis.call('PTTL', KEYS[2]), 1000)
                return {1, math.ceil(ttl / 1000)}
            end
            return {0, 0}
            """, List.class);
    private static final DefaultRedisScript<List> RECORD_SCRIPT = new DefaultRedisScript<>("""
            local userCount = redis.call('INCR', KEYS[1])
            if userCount == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[3]) end
            local ipCount = redis.call('INCR', KEYS[2])
            if ipCount == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[3]) end
            if userCount >= tonumber(ARGV[1]) or ipCount >= tonumber(ARGV[2]) then
                local ttl = math.max(redis.call('PTTL', KEYS[1]), redis.call('PTTL', KEYS[2]), 1000)
                return {1, math.ceil(ttl / 1000)}
            end
            return {0, 0}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final RedisFeatureProperties properties;

    public RedisLoginAttemptService(StringRedisTemplate redisTemplate,
                                    RedisKeyFactory keyFactory,
                                    RedisFeatureProperties properties) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.properties = properties;
    }

    @Override
    public void assertAllowed(String username, String clientIp) {
        executeFailOpen(CHECK_SCRIPT, username, clientIp);
    }

    @Override
    public void recordFailure(String username, String clientIp) {
        executeFailOpen(RECORD_SCRIPT, username, clientIp);
    }

    @Override
    public void recordSuccess(String username) {
        try {
            redisTemplate.delete(keyFactory.loginUsernameFailures(username));
        } catch (DataAccessException exception) {
            log.warn("Login limiter Redis cleanup unavailable; authentication remains available");
        }
    }

    private void executeFailOpen(DefaultRedisScript<List> script, String username, String clientIp) {
        try {
            List<?> result = redisTemplate.execute(
                    script,
                    List.of(keyFactory.loginUsernameFailures(username), keyFactory.loginIpFailures(clientIp)),
                    String.valueOf(properties.getLogin().getUsernameMaxFailures()),
                    String.valueOf(properties.getLogin().getIpMaxFailures()),
                    String.valueOf(properties.getLogin().getWindow().toMillis())
            );
            if (result != null && number(result, 0) == 1) {
                throw new RateLimitException("登录失败次数过多，请稍后重试", number(result, 1));
            }
        } catch (RateLimitException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            // 登录限流采用 fail-open，Redis 故障不能阻断合法用户登录。
            log.warn("Login limiter Redis unavailable; continuing with credential authentication");
        }
    }

    private long number(List<?> values, int index) {
        Object value = values.get(index);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
