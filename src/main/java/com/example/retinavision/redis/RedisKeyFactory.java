package com.example.retinavision.redis;

import com.example.retinavision.config.RedisFeatureProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class RedisKeyFactory {

    private final RedisFeatureProperties properties;

    public RedisKeyFactory(RedisFeatureProperties properties) {
        this.properties = properties;
    }

    public String loginUsernameFailures(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return prefix() + ":auth:login:fail:user:" + sha256(normalized);
    }

    public String loginIpFailures(String ip) {
        return prefix() + ":auth:login:fail:ip:" + sha256(ip == null ? "unknown" : ip.trim());
    }

    public String jwtBlacklist(String jti) {
        return prefix() + ":auth:jwt:blacklist:" + jti;
    }

    public String aiMinuteQuota(Integer userId, String window) {
        return prefix() + ":ai:submit:minute:" + userId + ":" + window;
    }

    public String aiDayQuota(Integer userId, String day) {
        return prefix() + ":ai:submit:day:" + userId + ":" + day;
    }

    private String prefix() {
        return properties.getKeyPrefix();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
