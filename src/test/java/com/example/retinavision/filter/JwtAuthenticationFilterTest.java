package com.example.retinavision.filter;

import com.example.retinavision.service.JwtBlacklistService;
import com.example.retinavision.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private JwtBlacklistService jwtBlacklistService;
    @Mock private Claims claims;
    @Mock private FilterChain filterChain;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil, new ObjectMapper(), jwtBlacklistService);
    }

    @Test
    void blacklistedTokenIsRejectedBeforeFilterChain() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.parseToken("token-value")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(jwtBlacklistService.isBlacklisted("jti-1")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void tokenWithoutJtiIsRejected() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.parseToken("token-value")).thenReturn(claims);
        when(claims.getId()).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void blacklistedTokenMayOnlyReachLogoutForIdempotentLogout() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        request.setServletPath("/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.parseToken("token-value")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(jwtBlacklistService.isBlacklisted("jti-1")).thenReturn(true);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("username", String.class)).thenReturn("alice");
        when(claims.get("roleCode", String.class)).thenReturn("DOCTOR");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-value");
        return request;
    }
}
