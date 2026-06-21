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

    // 生成登录用户名失败次数的 Redis 键
    public String loginUsernameFailures(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return prefix() + ":auth:login:fail:user:" + sha256(normalized);
    }

    // 生成登录 IP 失败次数的 Redis 键
    public String loginIpFailures(String ip) {
        return prefix() + ":auth:login:fail:ip:" + sha256(ip == null ? "unknown" : ip.trim());
    }
    // 生成 JWT 黑名单的 Redis 键
    public String jwtBlacklist(String jti) {
        return prefix() + ":auth:jwt:blacklist:" + jti;
    }

    // 生成 AI 提交分钟配额的 Redis 键
    public String aiMinuteQuota(Integer userId, String window) {
        return prefix() + ":ai:submit:minute:" + userId + ":" + window;
    }

    // 生成 AI 提交日配额的 Redis 键
    public String aiDayQuota(Integer userId, String day) {
        return prefix() + ":ai:submit:day:" + userId + ":" + day;
    }

    private String prefix() {
        return properties.getKeyPrefix();
    }

    // Hash the input value using SHA-256 and return the hexadecimal representation.
    //  对输入值进行 SHA-256 哈希处理，返回十六进制表示
    private String sha256(String value) {
        try {
            //getInstance("SHA-256") 获取 SHA-256 消息摘要算法的实例
            //digest(value.getBytes(StandardCharsets.UTF_8)) 将输入值转换为字节数组，并计算其 SHA-256 哈希值
            //HexFormat.of().formatHex(digest) 将字节数组转换为十六进制字符串
            //返回十六进制表示的哈希值
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
