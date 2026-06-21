package com.example.retinavision.service.impl;

import com.example.retinavision.config.RedisFeatureProperties;
import com.example.retinavision.exception.RateLimitException;
import com.example.retinavision.exception.RedisUnavailableException;
import com.example.retinavision.redis.RedisKeyFactory;
import com.example.retinavision.service.AiQuotaReservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisAiTaskQuotaServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    private RedisAiTaskQuotaService service;

    @BeforeEach
    void setUp() {
        RedisFeatureProperties properties = new RedisFeatureProperties();
        service = new RedisAiTaskQuotaService(
                redisTemplate,
                new RedisKeyFactory(properties),
                properties
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulReservationReturnsBothCounterKeys() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 0L));

        AiQuotaReservation reservation = service.reserve(7);

        assertThat(reservation.minuteKey()).startsWith("rv:ai:submit:minute:7:");
        assertThat(reservation.dayKey()).startsWith("rv:ai:submit:day:7:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectedReservationExposesRetryTime() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 17L));

        assertThatThrownBy(() -> service.reserve(7))
                .isInstanceOf(RateLimitException.class)
                .hasFieldOrPropertyWithValue("retryAfterSeconds", 17L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void quotaFailsClosedWhenRedisIsUnavailable() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("offline"));

        assertThatThrownBy(() -> service.reserve(7))
                .isInstanceOf(RedisUnavailableException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void releaseUsesAtomicCompensationScript() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(1L);
        AiQuotaReservation reservation = new AiQuotaReservation("minute-key", "day-key");

        service.release(reservation);

        verify(redisTemplate).execute(any(RedisScript.class), anyList());
    }
}
