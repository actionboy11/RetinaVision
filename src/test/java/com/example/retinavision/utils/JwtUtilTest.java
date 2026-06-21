package com.example.retinavision.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "retina-vision-test-secret-retina-vision-test-secret");
        ReflectionTestUtils.setField(jwtUtil, "expireHours", 24L);
    }

    @Test
    void generatedTokensContainUniqueJti() {
        Claims first = jwtUtil.parseToken(jwtUtil.generateToken(1, "alice", "DOCTOR"));
        Claims second = jwtUtil.parseToken(jwtUtil.generateToken(1, "alice", "DOCTOR"));

        assertThat(first.getId()).isNotBlank();
        assertThat(second.getId()).isNotBlank().isNotEqualTo(first.getId());
    }

    @Test
    void remainingLifetimeIsPositiveAndBoundedByExpiration() {
        Claims claims = jwtUtil.parseToken(jwtUtil.generateToken(1, "alice", "DOCTOR"));

        long remaining = jwtUtil.remainingLifetimeSeconds(claims);

        assertThat(remaining).isBetween(1L, 24L * 60 * 60);
    }
}
