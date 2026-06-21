package com.example.retinavision.redis;

import com.example.retinavision.config.RedisFeatureProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKeyFactoryTest {

    @Test
    void loginKeysDoNotExposeUsernameOrIp() {
        RedisFeatureProperties properties = new RedisFeatureProperties();
        RedisKeyFactory keyFactory = new RedisKeyFactory(properties);

        String userKey = keyFactory.loginUsernameFailures(" Alice ");
        String ipKey = keyFactory.loginIpFailures("127.0.0.1");

        assertThat(userKey).startsWith("rv:auth:login:fail:user:")
                .doesNotContain("Alice", "alice");
        assertThat(ipKey).startsWith("rv:auth:login:fail:ip:")
                .doesNotContain("127.0.0.1");
    }

    @Test
    void quotaAndBlacklistKeysUseStableNamespace() {
        RedisFeatureProperties properties = new RedisFeatureProperties();
        RedisKeyFactory keyFactory = new RedisKeyFactory(properties);

        assertThat(keyFactory.jwtBlacklist("token-id"))
                .isEqualTo("rv:auth:jwt:blacklist:token-id");
        assertThat(keyFactory.aiMinuteQuota(7, "202606210945"))
                .isEqualTo("rv:ai:submit:minute:7:202606210945");
        assertThat(keyFactory.aiDayQuota(7, "20260621"))
                .isEqualTo("rv:ai:submit:day:7:20260621");
    }
}
