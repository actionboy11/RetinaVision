package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.AccountNotFoundException;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.DTO.UserLoginDTO;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.service.LoginAttemptService;
import com.example.retinavision.service.JwtBlacklistService;
import com.example.retinavision.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Duration;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceImplTest {

    @Mock private UserRegisterMapper userRegisterMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private JwtBlacklistService jwtBlacklistService;
    @Mock private Claims claims;

    private UserLoginServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserLoginServiceImpl(
                userRegisterMapper,
                passwordEncoder,
                jwtUtil,
                loginAttemptService,
                jwtBlacklistService
        );
    }

    @Test
    void logoutBlacklistsJtiForRemainingTokenLifetime() {
        when(jwtUtil.parseToken("token-value")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(jwtUtil.remainingLifetimeSeconds(claims)).thenReturn(90L);

        service.logout("Bearer token-value");

        verify(jwtBlacklistService).blacklist("jti-1", Duration.ofSeconds(90));
    }

    @Test
    void failedPasswordRecordsUsernameAndIpFailure() {
        UserEntity user = user("alice", "hash");
        when(userRegisterMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.UserLogin(login("alice", "wrong"), "127.0.0.1"))
                .isInstanceOf(AccountNotFoundException.class);

        verify(loginAttemptService).assertAllowed("alice", "127.0.0.1");
        verify(loginAttemptService).recordFailure("alice", "127.0.0.1");
    }

    @Test
    void successfulLoginClearsOnlyUsernameFailures() {
        UserEntity user = user("alice", "hash");
        when(userRegisterMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtUtil.generateToken(1, "alice", "DOCTOR")).thenReturn("jwt");

        service.UserLogin(login("alice", "secret"), "127.0.0.1");

        verify(loginAttemptService).assertAllowed("alice", "127.0.0.1");
        verify(loginAttemptService).recordSuccess("alice");
    }

    private UserLoginDTO login(String username, String password) {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private UserEntity user(String username, String hash) {
        UserEntity user = new UserEntity();
        user.setId(1);
        user.setUsername(username);
        user.setPasswordHash(hash);
        user.setRealName("Alice");
        user.setRoleCode(UserRole.DOCTOR);
        user.setStatus(1);
        return user;
    }
}
