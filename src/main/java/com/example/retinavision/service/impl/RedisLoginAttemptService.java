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

/**
 * Redis 实现的登录尝试服务，用于检查和记录登录尝试次数。
 * 该服务将负责管理登录尝试次数的检查和记录逻辑
 */
public class RedisLoginAttemptService implements LoginAttemptService {

    // RedisTemplate 用于与 Redis 进行交互，提供了对 Redis 数据库的操作方法
    // Logger 用于记录日志信息，便于调试和监控 登录尝试服务的运行状态
    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptService.class);
    // Redis 脚本用于检查登录尝试次数是否超过限制，返回是否允许登录和过期时间（秒）的列表
    // Lua 脚本最重要的作用是：把多个 Redis 操作组合成一个不可被其他请求插入的原子操作，从而保证数据的一致性和正确性。
    // 其他请求只能看到脚本执行前或执行后的状态，看不到执行到一半的状态
    //redis.call() 是 Redis 提供的 Lua 脚本命令，用于在 Lua 脚本中调用 Redis 的命令。
    // 它可以执行各种 Redis 操作，如获取键值、设置键值、删除键值等。
    // PTTL 命令用于获取键的剩余过期时间（以毫秒为单位），如果键不存在或没有设置过期时间，则返回 -1。
    private static final DefaultRedisScript<List> CHECK_SCRIPT = new DefaultRedisScript<>("""
            local userCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            local ipCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if userCount >= tonumber(ARGV[1]) or ipCount >= tonumber(ARGV[2]) then
                local ttl = math.max(redis.call('PTTL', KEYS[1]), redis.call('PTTL', KEYS[2]), 1000)
                return {1, math.ceil(ttl / 1000)}
            end
            return {0, 0}
            """, List.class);
    // Redis 脚本用于记录登录失败次数，并设置过期时间（毫秒），返回是否超过限制和过期时间（秒）的列表
    //PEXPIRE 命令用于为键设置过期时间（以毫秒为单位），当键过期后会被自动删除。
    // ARGV[3] 是脚本传入的第三个参数，表示窗口时间（毫秒）。当登录失败次数第一次增加时，设置过期时间为窗口时间，以便在窗口时间内统计登录失败次数。
    //INCR 命令用于将键的整数值增加 1，如果键不存在，则会先将键设置为 0，然后再执行增加操作。
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
        // 检查登录尝试是否允许
        executeFailOpen(CHECK_SCRIPT, username, clientIp);
    }

    @Override
    public void recordFailure(String username, String clientIp) {
        // 记录登录失败次数，并设置过期时间（毫秒）为 window 时间
        executeFailOpen(RECORD_SCRIPT, username, clientIp);
    }

    @Override
    public void recordSuccess(String username) {
        try {
            // 登录成功后，删除登录失败次数键
            redisTemplate.delete(keyFactory.loginUsernameFailures(username));
        } catch (DataAccessException exception) {
            log.warn("Login limiter Redis cleanup unavailable; authentication remains available");
        }
    }

    private void executeFailOpen(DefaultRedisScript<List> script, String username, String clientIp) {
        try {
            //redisTemplate.execute(script, keys, args) 执行 Redis 脚本，传入键和参数，返回执行结果
            //execute 方法会将脚本发送到 Redis 服务器执行，并返回脚本执行的结果。
            // 键参数是一个列表，包含登录用户名失败次数键和登录 IP 失败次数键。参数列表包含登录用户名最大失败次数、登录 IP 最大失败次数和窗口时间（毫秒）。
            // 脚本会根据这些参数检查或记录登录尝试次数，并返回是否超过限制和过期时间（秒）的列表。
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
        //instanceof 检查 value 是否为 Number 类型，如果是则将其转换为 long 类型，否则将其转换为字符串再解析为 long 类型
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
