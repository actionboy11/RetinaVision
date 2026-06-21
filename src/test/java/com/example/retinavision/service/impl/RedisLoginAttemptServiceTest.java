package com.example.retinavision.service.impl;

import com.example.retinavision.config.RedisFeatureProperties;
import com.example.retinavision.exception.RateLimitException;
import com.example.retinavision.redis.RedisKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLoginAttemptServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    private RedisKeyFactory keyFactory;
    private RedisLoginAttemptService service;

    @BeforeEach
    void setUp() {
        RedisFeatureProperties properties = new RedisFeatureProperties();
        keyFactory = new RedisKeyFactory(properties);
        service = new RedisLoginAttemptService(redisTemplate, keyFactory, properties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordFailureThrowsRateLimitWithRedisRetryTime() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 42L));

        assertThatThrownBy(() -> service.recordFailure("alice", "127.0.0.1"))
                .isInstanceOf(RateLimitException.class)
                .hasFieldOrPropertyWithValue("retryAfterSeconds", 42L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureDoesNotBlockCredentialAuthentication() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("offline"));

        assertThatCode(() -> service.assertAllowed("alice", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void successfulLoginClearsOnlyUsernameKey() {
        service.recordSuccess("alice");

        verify(redisTemplate).delete(keyFactory.loginUsernameFailures("alice"));
    }
}
