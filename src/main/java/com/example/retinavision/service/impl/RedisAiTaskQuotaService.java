package com.example.retinavision.service.impl;

import com.example.retinavision.config.RedisFeatureProperties;
import com.example.retinavision.exception.RateLimitException;
import com.example.retinavision.exception.RedisUnavailableException;
import com.example.retinavision.redis.RedisKeyFactory;
import com.example.retinavision.service.AiQuotaReservation;
import com.example.retinavision.service.AiTaskQuotaService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RedisAiTaskQuotaService implements AiTaskQuotaService {

    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    // Redis 脚本用于检查和预留 AI 任务配额，返回是否成功预留和剩余时间（秒）的列表
    private static final DefaultRedisScript<List> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local minuteCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            local dayCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if minuteCount >= tonumber(ARGV[1]) then return {0, tonumber(ARGV[3])} end
            if dayCount >= tonumber(ARGV[2]) then return {0, tonumber(ARGV[4])} end
            minuteCount = redis.call('INCR', KEYS[1])
            if minuteCount == 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
            dayCount = redis.call('INCR', KEYS[2])
            if dayCount == 1 then redis.call('EXPIRE', KEYS[2], ARGV[4]) end
            return {1, 0}
            """, List.class);
    // Redis 脚本用于释放 AI 任务配额，减少分钟和日计数，如果计数为 0，则删除键，否则减少计数并返回 1 表示成功
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            for i = 1, 2 do
                local value = tonumber(redis.call('GET', KEYS[i]) or '0')
                if value <= 1 then
                    redis.call('DEL', KEYS[i])
                else
                    redis.call('DECR', KEYS[i])
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final RedisFeatureProperties properties;

    public RedisAiTaskQuotaService(StringRedisTemplate redisTemplate,
                                   RedisKeyFactory keyFactory,
                                   RedisFeatureProperties properties) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.properties = properties;
    }

    // 预留 AI 任务配额，返回预留的分钟和日配额键
    @Override
    public AiQuotaReservation reserve(Integer userId) {
        //ZoneId.of() 方法根据配置的时区 ID 获取对应的 ZoneId 对象，ZonedDateTime.now() 方法获取当前时间，并使用指定的时区进行计算。
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(properties.getAi().getZoneId()));
        // 计算预留的分钟和日配额的过期时间（秒），确保在过期时间内可以提交任务。
        //now.truncatedTo(ChronoUnit.MINUTES) 将当前时间截断到分钟级别，
        // plusMinutes(1) 表示加上 1 分钟，Duration.between() 方法计算两个时间点之间的持续时间.
        long minuteTtl = Math.max(1, Duration.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).getSeconds());
        long dayTtl = Math.max(1, Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.getZone())).getSeconds());
        AiQuotaReservation reservation = new AiQuotaReservation(
                keyFactory.aiMinuteQuota(userId, MINUTE_FORMAT.format(now)),
                keyFactory.aiDayQuota(userId, DAY_FORMAT.format(now))
        );
        try {
            List<?> result = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(reservation.minuteKey(), reservation.dayKey()),
                    String.valueOf(properties.getAi().getMinuteLimit()),
                    String.valueOf(properties.getAi().getDailyLimit()),
                    String.valueOf(minuteTtl),
                    String.valueOf(dayTtl)
            );
            if (result == null || result.size() < 2) {
                throw new RedisUnavailableException("AI 配额服务暂时不可用，请稍后重试");
            }
            if (number(result, 0) != 1) {
                throw new RateLimitException("AI 任务提交已达到配额限制", number(result, 1));
            }
            return reservation;
        } catch (RateLimitException | RedisUnavailableException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new RedisUnavailableException("AI 配额服务暂时不可用，请稍后重试", exception);
        }
    }

    // 释放 AI 任务配额，减少分钟和日计数，如果计数为 0，则删除键，否则减少计数并返回 1 表示成功
    @Override
    public void release(AiQuotaReservation reservation) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(reservation.minuteKey(), reservation.dayKey()));
        } catch (DataAccessException exception) {
            throw new RedisUnavailableException("AI 配额补偿暂时不可用", exception);
        }
    }

    private long number(List<?> values, int index) {
        Object value = values.get(index);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
