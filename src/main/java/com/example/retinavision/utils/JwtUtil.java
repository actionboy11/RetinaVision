package com.example.retinavision.utils;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
@Component
public class JwtUtil {

    @Value("${retina.jwt.secret}")
    private String secret;

    @Value("${retina.jwt.expire-hours}")
    private Long expireHours;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Integer userId, String username, String roleCode) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireHours * 60 * 60 * 1000);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())    // 设置 JWT 的唯一标识符
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roleCode", roleCode)
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long remainingLifetimeSeconds(Claims claims) {
        // 获取 JWT 的过期时间，并计算剩余的有效时间（以秒为单位）。如果 JWT 已经过期，则返回 0。
        //toInstant() 将 Date 转换为 Instant 对象，方便进行时间计算
        Instant expiration = claims.getExpiration().toInstant();
        return Math.max(0, Duration.between(Instant.now(), expiration).getSeconds());
    }
}
