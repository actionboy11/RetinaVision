package com.example.retinavision.service.impl;

import com.example.retinavision.config.RedisFeatureProperties;
import com.example.retinavision.exception.RedisUnavailableException;
import com.example.retinavision.redis.RedisKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisJwtBlacklistServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    private RedisKeyFactory keyFactory;
    private RedisJwtBlacklistService service;

    @BeforeEach
    void setUp() {
        keyFactory = new RedisKeyFactory(new RedisFeatureProperties());
        service = new RedisJwtBlacklistService(redisTemplate, keyFactory);
    }

    @Test
    void blacklistStoresOnlyMarkerForRemainingLifetime() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.blacklist("jti-1", Duration.ofSeconds(90));

        verify(valueOperations).set(keyFactory.jwtBlacklist("jti-1"), "1", Duration.ofSeconds(90));
    }

    @Test
    void lookupFailsClosedWhenRedisIsUnavailable() {
        when(redisTemplate.hasKey(keyFactory.jwtBlacklist("jti-1")))
                .thenThrow(new RedisConnectionFailureException("offline"));

        assertThatThrownBy(() -> service.isBlacklisted("jti-1"))
                .isInstanceOf(RedisUnavailableException.class);
    }
}
